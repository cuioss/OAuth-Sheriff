# JWT Signature Validation Performance Optimization Plan

## ⚠️Comply Project rule
Always comply with the project rules: [CLAUDE.md]

## ⚠️ Breaking Changes Notice (Pre-1.0)

Since this library is in pre-1.0 state, these optimizations may introduce breaking changes without deprecation warnings or migration paths. Changes include:

- Architecture change: `TokenSignatureValidator` becomes a field in `TokenValidator`
- API modification: `SignatureTemplateManager` constructor now requires `SignatureAlgorithmPreferences`
- Enhancement: `SignatureTemplateManager` includes Provider bypass logic
- Package movement: Classes may move to `de.cuioss.jwt.validation.pipeline.signature` if not already there

## Executive Summary

This document outlines a streamlined optimization strategy for improving JWT signature validation performance in the CUI-JWT library. The plan focuses on the critical architecture fix (making TokenSignatureValidator a field), enhancing SignatureTemplateManager with Provider bypass, and ensuring virtual thread compatibility.

## 🚀 Actionable Tasks (Priority Order)

### Phase 1: Critical Architecture Fix (MUST DO FIRST)
- [ ] **Refactor TokenValidator** to make TokenSignatureValidator a field instead of creating new instances
- [ ] **Update TokenSignatureValidator constructor** to accept SignatureAlgorithmPreferences
- [ ] **Pass SignatureAlgorithmPreferences** through the initialization chain

### Phase 2: Core Optimizations  
- [ ] **Enhance SignatureTemplateManager** with Provider bypass logic and SignatureAlgorithmPreferences
- [ ] **Update SignatureTemplateManager constructor** to accept SignatureAlgorithmPreferences
- [ ] **Add Provider pre-discovery logic** to SignatureTemplateManager initialization
- [ ] **Move SignatureTemplateManager** to `de.cuioss.jwt.validation.pipeline.signature` package if not already there
- [ ] **Add unit tests** for enhanced SignatureTemplateManager

### Phase 3: Virtual Thread Compatibility
- [ ] **Audit Concurrency Patterns** - Run comprehensive scan for virtual thread anti-patterns
  - [ ] Search for ThreadLocal usage: `grep -r "ThreadLocal" cui-jwt-validation/src/`
  - [ ] Identify synchronized blocks: `grep -r "synchronized" cui-jwt-validation/src/`
  - [ ] Check external dependencies for virtual thread compatibility
  - [ ] Add pinning detection: `-Djdk.tracePinnedThreads=full` to [quarkus-integration-benchmark](../cui-jwt-quarkus-parent/quarkus-integration-benchmark)
- [ ] **Replace problematic synchronized blocks** with ReentrantLock (Java 21-23 compatibility)

### Phase 4: Integration & Final Validation
- [ ] **Integration testing** with existing TokenSignatureValidator
- [ ] **Thread safety validation** under high concurrency using existing JMH benchmarks
- [ ] **Update package imports** throughout codebase
- [ ] **Finally verify using quarkus-integration-benchmark**

## Current State Analysis

### Existing Optimizations
- **SignatureTemplateManager**: Already implements template caching for algorithm mappings and PSS parameters
- **Per-request Signature instances**: Current implementation creates fresh Signature instances to ensure thread safety
- **Efficient algorithm mapping**: Switch expressions for O(1) algorithm lookups

### Performance Bottlenecks Identified
1. **Provider.getService() contention**: Each Signature.getInstance() call triggers Provider lookups with lock contention
2. **Signature instance creation overhead**: Creating new instances for each validation without any reuse

## Optimization 1: Enhanced SignatureTemplateManager with Provider Bypass

### Technical Analysis

Based on SmallRye JWT issue #179, the real bottleneck is the synchronized `Provider.getService()` lookup that occurs inside every `Signature.getInstance()` call:

```java
// The actual bottleneck - synchronized Provider lookup
public static Signature getInstance(String algorithm) {
    // Internally calls Provider.getService() with global synchronization
    Provider.Service service = Provider.getService("Signature", algorithm);
    return (Signature) service.newInstance();
}
```

### The Real Problem
- **NOT** the cost of creating Signature instances (they're lightweight)
- **BUT** the synchronized global Provider registry lookup
- Under high concurrency, threads block waiting for this lock

### Why NOT to Pool Signature Instances
- **Signature objects are stateful** - they maintain internal state during operations
- **Not thread-safe** - cannot be shared between concurrent operations
- **Lightweight creation** - the actual object instantiation is fast
- **The bottleneck is elsewhere** - in the Provider lookup, not object creation

### Proposed Implementation

Enhance the existing SignatureTemplateManager to include Provider bypass:

```java
/**
 * Enhanced SignatureTemplateManager that bypasses synchronized Provider lookups.
 * Combines template caching with provider pre-configuration for optimal performance.
 */
public class SignatureTemplateManager {
    private static final CuiLogger LOGGER = new CuiLogger(SignatureTemplateManager.class);
    
    // Existing template cache
    private final ConcurrentHashMap<String, SignatureTemplate> signatureTemplateCache;
    
    // NEW: Pre-configured providers to bypass synchronized lookup
    private final Map<String, Provider> algorithmProviders;
    
    /**
     * Initializes the manager with runtime algorithm preferences.
     * Pre-discovers providers for configured algorithms to bypass Provider.getService().
     */
    public SignatureTemplateManager(SignatureAlgorithmPreferences preferences) {
        this.signatureTemplateCache = new ConcurrentHashMap<>();
        this.algorithmProviders = new HashMap<>();
        
        // Pre-discover providers for all configured algorithms
        for (String jwtAlgorithm : preferences.getPreferredAlgorithms()) {
            SignatureTemplate template = createSignatureTemplate(jwtAlgorithm);
            signatureTemplateCache.put(jwtAlgorithm, template);
            
            // Pre-configure provider for this algorithm
            String jdkAlgorithm = template.jdkAlgorithm();
            for (Provider provider : Security.getProviders()) {
                if (provider.getService("Signature", jdkAlgorithm) != null) {
                    algorithmProviders.put(jdkAlgorithm, provider);
                    LOGGER.debug("Pre-configured provider %s for algorithm %s", 
                                provider.getName(), jdkAlgorithm);
                    break;
                }
            }
        }
    }
    
    /**
     * Creates a Signature instance using pre-configured provider to bypass
     * synchronized Provider.getService() lookup.
     */
    Signature getSignatureInstance(String algorithm) {
        SignatureTemplate template = signatureTemplateCache.computeIfAbsent(
            algorithm, this::createSignatureTemplate);
        
        try {
            // Use pre-configured provider if available
            Provider provider = algorithmProviders.get(template.jdkAlgorithm());
            Signature signature = (provider != null) 
                ? Signature.getInstance(template.jdkAlgorithm(), provider)
                : Signature.getInstance(template.jdkAlgorithm());
                
            // Apply PSS parameters if needed
            if (template.pssParams() != null) {
                signature.setParameter(template.pssParams());
            }
            
            return signature;
            
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedAlgorithmException(
                "Algorithm no longer supported: " + template.jdkAlgorithm(), e);
        } catch (InvalidAlgorithmParameterException e) {
            throw new UnsupportedAlgorithmException(
                "Invalid PSS parameters: " + template.jdkAlgorithm(), e);
        }
    }
    
    // Existing SignatureTemplate record and createSignatureTemplate method remain unchanged
}
```

### Implementation Steps

1. **Enhance SignatureTemplateManager** in `de.cuioss.jwt.validation.pipeline.signature`
   - Modify constructor to accept SignatureAlgorithmPreferences
   - Add Provider pre-discovery logic in constructor
   - Update getSignatureInstance() to use pre-configured providers
   - Maintain backward compatibility for existing template logic

2. **Performance monitoring**
   ```java
   public class ProviderBypassMetrics {
       private final AtomicLong optimizedCreations = new AtomicLong();
       private final AtomicLong fallbackCreations = new AtomicLong();
       private final Map<String, AtomicLong> algorithmCounts = new ConcurrentHashMap<>();
       
       public double getOptimizationRate() {
           long total = optimizedCreations.get() + fallbackCreations.get();
           return total > 0 ? (double) optimizedCreations.get() / total : 0.0;
       }
   }
   ```

## Optimization 2: Simplified Architecture Without Unnecessary Complexity

### Technical Analysis

After careful analysis, we've determined that:

1. **Scoped Values are unnecessary** for Signature instances because:
   - Signature objects are NOT thread-safe and cannot be shared
   - They're lightweight to create (the bottleneck is Provider lookup, not instantiation)
   - No expensive resources to manage or cleanup required
   - Each operation needs a fresh instance anyway

2. **The real optimization** is simply:
   - Make TokenSignatureValidator a field (not recreated on every validation)
   - Enhance SignatureTemplateManager with Provider bypass
   - That's it - no complex patterns needed

### Clean Implementation

The TokenSignatureValidator simply uses the enhanced SignatureTemplateManager:

```java
public class TokenSignatureValidator {
    private final JwksLoader jwksLoader;
    private final SecurityEventCounter securityEventCounter;
    private final SignatureTemplateManager templateManager;
    
    public TokenSignatureValidator(JwksLoader jwksLoader, 
                                   SecurityEventCounter securityEventCounter,
                                   SignatureAlgorithmPreferences algorithmPreferences) {
        this.jwksLoader = jwksLoader;
        this.securityEventCounter = securityEventCounter;
        // Initialize enhanced template manager with Provider bypass
        this.templateManager = new SignatureTemplateManager(algorithmPreferences);
    }
    
    public void validateSignature(DecodedJwt jwt) throws TokenValidationException {
        String algorithm = jwt.getHeader().getAlgorithm();
        PublicKey publicKey = jwksLoader.getKey(jwt.getHeader().getKeyId());
        
        try {
            // Get optimized Signature instance (bypasses Provider lookup)
            Signature signature = templateManager.getSignatureInstance(algorithm);
            
            // Standard signature verification - no complex patterns needed
            signature.initVerify(publicKey);
            signature.update(jwt.getDataToVerify().getBytes(StandardCharsets.UTF_8));
            
            byte[] signatureBytes = convertSignatureIfNeeded(
                jwt.getSignatureAsDecodedBytes(), algorithm);
            
            if (!signature.verify(signatureBytes)) {
                securityEventCounter.increment(INVALID_SIGNATURE);
                throw new TokenValidationException("Invalid signature");
            }
            
        } catch (InvalidKeyException | SignatureException e) {
            securityEventCounter.increment(SIGNATURE_VERIFICATION_ERROR);
            throw new TokenValidationException("Signature verification failed", e);
        }
    }
}

### Implementation Steps

1. **Update TokenValidator** to make TokenSignatureValidator a field:
   ```java
   public class TokenValidator {
       private final TokenSignatureValidator signatureValidator;
       
       public TokenValidator(IssuerConfig issuerConfig, 
                            SignatureAlgorithmPreferences algorithmPreferences,
                            SecurityEventCounter securityEventCounter) {
           // Initialize once in constructor
           this.signatureValidator = new TokenSignatureValidator(
               issuerConfig.getJwksLoader(), 
               securityEventCounter,
               algorithmPreferences
           );
       }
       
       // In validate method (line 425), replace:
       // TokenSignatureValidator signatureValidator = new TokenSignatureValidator(jwksLoader, securityEventCounter);
       // With:
       signatureValidator.validateSignature(decodedJwt);
   }
   ```

2. **Enhance SignatureTemplateManager** with Provider bypass logic (as shown in Optimization 1)

3. **Key Architecture Changes**:
   - **TokenSignatureValidator** becomes a field in TokenValidator (created once)
   - **SignatureTemplateManager** enhanced with Provider bypass capability
   - No complex patterns like Scoped Values needed
   - Simple, clean, and effective

### Why This Architecture Change is Critical

The current implementation creates a new `TokenSignatureValidator` instance for every validation (line 425 in TokenValidator.java). This completely defeats any caching or optimization because:
- SignatureTemplateManager's cache is recreated every time
- No Provider lookup optimization can be effective
- Every validation pays the full cost of Provider.getService() synchronization

By making `TokenSignatureValidator` a field in `TokenValidator`, we enable:
- One-time Provider discovery at startup
- Effective caching of SignatureTemplate instances
- Amortization of initialization costs across all validations

### Key Benefits of Simplified Approach

1. **Eliminates Provider Contention**: Enhanced SignatureTemplateManager bypasses synchronized lookups
2. **Architecture Fix**: TokenSignatureValidator as a field enables effective caching
3. **Virtual Thread Optimized**: No ThreadLocal, no unnecessary complexity
4. **Clean and Simple**: Straightforward implementation without overengineering
5. **Measurable Performance Gains**: Addresses the 73.7% signature validation bottleneck
6. **Respects Runtime Configuration**: Uses SignatureAlgorithmPreferences instead of hardcoded algorithms

## Optimization 3: Virtual Thread Concurrency Pattern Audit & Migration

### Technical Analysis

Based on comprehensive research (see [Virtual Threads Concurrency Analysis](../virtual-threads-concurrency-analysis.md)), certain traditional concurrency patterns can negatively impact virtual thread performance or cause pinning. While Java 24+ has resolved many synchronized-related pinning issues, other anti-patterns remain problematic.

#### Current Codebase Assessment ✅

**Excellent Patterns Already in Use:**
- **Instance-based Caching**: `SignatureTemplateManager` uses `ConcurrentHashMap` instead of ThreadLocal
- **Lock-free Operations**: `SecurityEventCounter` uses atomic operations
- **Immutable Data Publishing**: `IssuerConfigResolver` properly handles concurrent updates
- **Minimal Synchronization**: Brief synchronized blocks for initialization only

#### Anti-patterns to Monitor

### 1. ThreadLocal Anti-pattern (Not present - ✅ Good)
```java
// BAD: Would cause massive memory usage with virtual threads
private static final ThreadLocal<Signature> SIGNATURE_CACHE = 
    ThreadLocal.withInitial(() -> createSignature());

// GOOD: Current approach in SignatureTemplateManager
private final ConcurrentHashMap<String, SignatureTemplate> cache = new ConcurrentHashMap<>();
```

### 2. Synchronized Patterns Analysis

**Current codebase synchronized usage assessment:**

```java
// ACCEPTABLE: Short-lived cache operations (IssuerConfigResolver.java)
synchronized (this) {
    IssuerConfig result = mutableCache.get(issuer);  // Fast in-memory only
    // ... double-check locking pattern
}

// REVIEW NEEDED: HTTP operations in synchronized (ETagAwareHttpHandler.java)
public synchronized LoadResult load() {
    HttpFetchResult result = fetchJwksContentWithCache();  // I/O operation
}

// BETTER FOR JAVA 21-23: Use ReentrantLock for I/O operations
private final ReentrantLock lock = new ReentrantLock();
public LoadResult load() {
    lock.lock();
    try {
        return fetchJwksContentWithCache();  // Virtual thread can unmount
    } finally {
        lock.unlock();
    }
}
```

**Key Findings:**
- **Java 24+**: JEP 491 resolves synchronized pinning - no migration needed
- **Java 21-23**: Consider ReentrantLock for HTTP-related synchronized blocks
- **Current code**: Mostly acceptable patterns, minimal virtual thread impact

### 3. Connection Pool Issues (External dependencies)
Traditional connection pools like HikariCP may have internal synchronization that affects virtual threads in Java 21-23.

### Implementation Steps

1. **Audit Current Concurrency Patterns**
   ```bash
   # Search for potential anti-patterns
   grep -r "ThreadLocal\|synchronized\|wait()\|notify()" cui-jwt-validation/src/
   ```

2. **Add Virtual Thread Pinning Detection** (Java 21-23)
   ```java
   // JVM argument for pinning detection
   -Djdk.tracePinnedThreads=full
   ```

3. **Migration Checklist**
   - [ ] **Audit for ThreadLocal patterns** (none found - ✅ good)
   - [ ] **Convert synchronized methods** to ReentrantLock where needed
   - [ ] **Audit external dependencies** for virtual thread compatibility
   - [ ] **Add pinning detection** in development/testing environments
   - [ ] **Performance testing** with virtual threads vs platform threads

### Virtual Thread Compatibility Matrix

#### ✅ **Positive Patterns (Current Code)**
- `ConcurrentHashMap` for caching
- `AtomicLong`/`AtomicReference` for counters
- Immutable objects with volatile publication
- Brief synchronized blocks for initialization
- `CompletableFuture` for async operations

#### ❌ **Anti-patterns to Avoid**
- `ThreadLocal` caching with virtual threads
- **Synchronized blocks with I/O operations** (Java 21-23) - causes virtual thread pinning
- **Lombok @Synchronized annotation** - uses synchronized internally, prefer @Locked for virtual threads
- **Long-running synchronized blocks** (Java 21-23) - resolved in Java 24+ with JEP 491
- Traditional connection pooling patterns
- `wait()`/`notify()` in high-contention scenarios

#### ⚠️ **Patterns Requiring Analysis**
- External HTTP client libraries
- Database connection pools
- Third-party caching libraries
- Native library integrations

### Integration with Existing Optimizations

This audit complements the other optimizations:
- **Optimization 1**: Enhanced SignatureTemplateManager eliminates Provider contention
- **Optimization 2**: Simplified architecture avoids overengineering and complexity
- Both ensure clean patterns that work well with virtual threads

## Summary

This optimization plan addresses the critical performance bottleneck in JWT signature validation, which currently consumes 73.7% of validation time. The streamlined approach includes:
1. **Enhanced SignatureTemplateManager** with Provider bypass to eliminate the synchronized getService() bottleneck
2. **Architecture fix** making TokenSignatureValidator a field instead of recreating it on every validation
3. **Virtual thread compatibility** audit to ensure optimal performance across all patterns

Key outcomes:
- **Critical architecture fix**: TokenSignatureValidator must be a field, not recreated on every validation
- **Eliminated contention**: Provider.getService() bottleneck bypassed through pre-configured providers
- **Simplified solution**: No unnecessary complexity like Scoped Values or object pooling
- **Virtual thread ready**: Clean patterns that work well with both platform and virtual threads
- **Runtime configuration**: Respects SignatureAlgorithmPreferences instead of hardcoded algorithms
- **Production-ready**: Based on real-world findings from SmallRye JWT issue #179

The implementation is clean, simple, and effective. By avoiding overengineering and focusing on the real bottlenecks, we achieve significant performance improvements with minimal code changes. **The architecture change in Phase 1 is mandatory** - without it, all other optimizations are ineffective.
