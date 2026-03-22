# Security Policy

## Reporting a Vulnerability

We built the **AI Build Integrity Maven Plugin** to secure the AI software development lifecycle. Because this tool sits at the absolute foundation of the supply chain, we take its security posture incredibly seriously.

If you have discovered a vulnerability that could allow an attacker to bypass the cryptographic ledger or manipulate the SIEM outputs, we need to know immediately.

**Please do not report security vulnerabilities through public GitHub issues.**

### How to Report

Please report security issues directly to our security response team:

- **Security Advisories**: Use the GitHub "Report a vulnerability" feature located in the Security tab of the repository.

Please provide a clear description of the vulnerability, including:

- A summary of the bypass or integrity flaw.
- Steps to reproduce the exploit against the hashing engine.
- The potential impact on downstream SIEM reporting.

We will immediately acknowledge your report and collaborate with you to patch the engine.

## Supported Versions

Only the latest stable version of the plugin is supported for security updates.

| Version |     Supported      |
|---------|--------------------|
| 0.9.x   | :white_check_mark: |
| < 0.9   | :x:                |

## Responsible Disclosure

We ask that you follow responsible disclosure principles:

- Give us a reasonable amount of time to patch the underlying Maven Mojo interceptors before publishing the exploit publicly.
- Do not exploit the vulnerability beyond what is necessary for a proof of concept.

Thank you for helping us keep the global AI supply chain secure!

## Vulnerability Management Policy

We are committed to the proactive discovery and timely remediation of security vulnerabilities.

### Internal Scanning

- **Static Analysis:** This project uses GitHub CodeQL and other static analysis tools on every Pull Request and on a weekly scheduled basis.
- **Triage:** All "Medium" or higher severity findings are triaged by maintainers within 5 business days to confirm exploitability.

### Remediation Timelines

Confirmed exploitable vulnerabilities will be remediated according to the following schedule:

- **Critical/High Severity:** Fixed within 30 days of confirmation.
- **Medium Severity:** Fixed within 90 days of confirmation.

### False Positives

Findings determined to be non-exploitable or false positives will be dismissed in the scanning tool with a documented justification.
