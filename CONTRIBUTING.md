# Contributing to OAuth Sheriff

Thank you for your interest in OAuth Sheriff! We welcome contributions from the community.

## Contributor License Agreement (CLA)

Before we can accept your contribution, you must sign our Contributor License Agreement (CLA) once. This happens automatically when you create your first Pull Request via the [CLA Assistant](https://cla-assistant.io/).

### Why a CLA?

The CLA ensures that:
- You have the right to contribute the code
- We have the necessary rights to distribute the code under the Apache 2.0 license
- Your contribution can be freely used by the community

## How to Contribute

### Reporting Issues

- First check if a similar issue already exists
- Use issue templates if available
- Describe the problem or suggestion in as much detail as possible

### Security Issues

Please do not report security vulnerabilities through public GitHub issues. Use GitHub's private vulnerability reporting feature instead.

### Pull Requests

1. **Fork** the repository
2. Create a **feature branch** (`git checkout -b feature/my-improvement`)
3. **Build and verify** your changes (see below)
4. **Commit** your changes with meaningful commit messages
5. **Push** the branch (`git push origin feature/my-improvement`)
6. Create a **Pull Request**

### Before Committing

Before committing and pushing, run these two builds and fix all issues:

```bash
# 1. Run code quality checks (checkstyle, spotbugs, PMD)
./mvnw -Ppre-commit clean verify

# 2. Run full build with all tests
./mvnw clean install
```

Both builds must pass without errors or warnings before committing.

### Code Standards

- Follow the existing code conventions in the project
- Write tests for new functionality
- Document public APIs
- Keep commits atomic and traceable

### Project Documentation

Please familiarize yourself with the project rules and specifications:

**Build & Development:**
- [Build Guide](doc/Build.adoc) - Build instructions and profiles
- [Log Messages](doc/LogMessages.adoc) - Logging standards and LogRecord usage

**Specifications:**
- [Specification](doc/Specification.adoc) - Core functionality specification
- [Requirements](doc/Requirements.adoc) - Project requirements
- [Testing Standards](doc/specification/testing.adoc) - Testing guidelines and practices

**Security:**
- [Security Specifications](doc/security/security-specifications.adoc) - Security requirements
- [Threat Model](doc/security/Threat-Model.adoc) - Security threat analysis
- [JWT Security Best Practices](doc/security/jwt-security-best-practices.adoc) - JWT handling guidelines

### Commit Messages

We recommend [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add new feature
fix: fix bug in XY
docs: update documentation
refactor: improve code structure
test: add or update tests
```

## Code of Conduct

We expect all contributors to interact respectfully and constructively. Insults, discrimination, or other inappropriate behavior will not be tolerated.

## License

By contributing, you agree that your contribution will be published under the [Apache License 2.0](LICENSE).

## Questions?

If you have questions, feel free to create an issue or contact the maintainers directly.

---

Thank you again for your contribution!
