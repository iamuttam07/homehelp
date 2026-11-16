# 1. Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-11-16

## Context

HomeHelp is built step by step, and many choices (frameworks, data stores, patterns) shape everything that comes after. Without a written record, the reasons behind those choices get lost, and new contributors have to guess or reverse-engineer them.

## Decision

We record significant architecture decisions as Architecture Decision Records (ADRs), following the lightweight format described by Michael Nygard.

- ADRs live in `docs/adr/` as Markdown files named `NNNN-short-title.md`, numbered in order.
- Each ADR has these sections: **Status**, **Date**, **Context**, **Decision**, and **Consequences**.
- Status is one of `Proposed`, `Accepted`, `Deprecated`, or `Superseded by ADR NNNN`.
- An accepted ADR is not rewritten. If a decision changes, a new ADR supersedes it, and the old one's status is updated to point to the new one.
- An ADR is added in the same pull request as the change it describes.

A decision is "significant" if it introduces a new technology, sets a project-wide pattern, or is costly to reverse.

## Consequences

- Reasons for decisions are kept alongside the code and reviewed like code.
- Writing an ADR takes a little extra time per significant change.
- The ADR list doubles as a history of how the architecture evolved.
