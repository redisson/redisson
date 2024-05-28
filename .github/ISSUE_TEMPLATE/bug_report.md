name: 🐞 Bug
description: Create a report to help us improve
body:
  - type: markdown
    attributes:
      value: |
        Thank you for reporting an issue.

        Upgrade to [Redisson PRO](https://redisson.pro) with **advanced features** and **support with SLA**.
  - type: input
    attributes:
      label: Redis version
    validations:
      required: true
  - type: input
    attributes:
      label: Redisson version
    validations:
      required: true
  - type: input
    attributes:
      label: Redisson configuration
    validations:
      required: true
  - type: textarea
    attributes:
      label: What is the Expected behavior?
      description: If possible please provide textual output instead of screenshots.
  - type: textarea
    attributes:
      label: What is the Actual behavior?
      description: If possible please provide textual output instead of screenshots.
    validations:
      required: true
  - type: textarea
    attributes:
      label: Additional information
      description: Tell us anything else you think we should know.
