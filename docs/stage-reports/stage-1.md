# Stage 1 · Requirements analysis and system design

## Objective

Establish a reviewed design and verifiable requirements for the Stage 2–6 implementation.

## Implementation

Inspected the workspace and found no existing project files, POM, source or Git repository. Established Java 21, Maven, Swing/AWT, FlatLaf, Jackson, JUnit 5 and local JSON as the implementation baseline. Documented the requirements, acceptance checks, domain ownership, MVC layers, persistence boundaries and extension points through Stage 12.

## Files created

- `docs/SRS.md`, `docs/architecture.md`, `docs/design-decisions.md` and `docs/development-log.md`.
- `docs/uml/` with paired PlantUML and PNG files for use cases, classes, task creation activity, task creation and save sequence, task lifecycle and components.
- `docs/stage-reports/stage-1.md`.

## Files modified

This stage initialized the project and introduced all tracked files.

## Java concepts

The design assigns packages, inheritance, abstract classes, interface contracts, composition, association, polymorphism and the EDT lifecycle. Executable Java coverage begins in Stage 2.

## Design patterns

MVC separates the Swing UI from application rules. Repositories hide storage. TaskFactory centralizes subtype creation and Strategy supports comparator selection. Workspace membership uses direct ID references, with a Composite considered when nested folders arrive.

## Testing and review

- The independent design review checked the SRS, architecture, decisions and all six UML sources. It identified two clarifications, which were incorporated. Query lists use roots while subject listings and counts include associated subtasks. Recurrence resets actual duration and exam preparation, preserves assignment metadata and records whether the predecessor has already advanced.
- Ran PlantUML 1.2025.10 syntax validation and decoded all six rendered PNGs successfully.
- Ran `java -version`, `javac -version` and `mvn -version`. The original shell exposed Java 8 and lacked `javac` and Maven. Verified a workspace-local OpenJDK 21.0.13 and Maven 3.9.16 for the implementation stages.

To regenerate the PNGs from the project directory with a local PlantUML JAR:

```powershell
java -jar "C:\path\to\plantuml-1.2025.10.jar" -checkonly 'docs/uml/*.puml'
java -jar "C:\path\to\plantuml-1.2025.10.jar" -tpng 'docs/uml/*.puml'
```

## Issues and decisions

The brief's task-to-subject object association is modeled through an ID resolved by the service. Upcoming covers tomorrow through day seven. Stage 6 includes a basic restore because its specific requirements call for one. The review found no open design blocker.

## Deliverables and next stage

The reviewed specification, architecture, decisions, log and six diagram pairs are ready. Stage 2 can configure Maven and launch the initial Swing shell using the verified Java 21 toolchain. Application compilation and unit tests start with that foundation.
