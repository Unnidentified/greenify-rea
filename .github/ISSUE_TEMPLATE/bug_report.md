name: Bug Report
description: Report a crash, hibernation issue, or UI bug in Greenify Reborn
title: "[Bug]: "
labels: ["bug"]
body:
  - type: textarea
    id: description
    attributes:
      label: Describe the bug
      description: A clear description of what happened vs. what you expected.
    validations:
      required: true
  - type: dropdown
    id: mode
    attributes:
      label: Working Mode
      options:
        - Shizuku
        - Root (Magisk / KernelSU / APatch)
        - Non-Root / Accessibility
        - Auto
    validations:
      required: true
  - type: input
    id: android_version
    attributes:
      label: Android Version & Device / ROM
      placeholder: "e.g., Android 15, Pixel 8 / OnePlus"
    validations:
      required: true
  - type: textarea
    id: logs
    attributes:
      label: Steps to reproduce & relevant logcat
      render: text
