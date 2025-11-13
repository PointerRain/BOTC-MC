# BOTC-MC

A beginner-friendly guide to build, run, and contribute to this Minecraft Fabric mod on Windows using IntelliJ IDEA. No prior Java experience required.

This project uses Gradle with Fabric Loom. The Gradle wrapper is included, so you do not need to install Gradle separately.

## What you’ll do
1) Install tools (JDK, Git, IntelliJ IDEA)
2) Clone this repository in IntelliJ
3) Let Gradle set up automatically
4) Run the dev server and accept the EULA
5) Build the mod JAR
6) Use the Feature Branch Workflow to contribute changes

---

## 1) Install tools (Windows)
- Java Development Kit (JDK):
  - Temurin (Adoptium): https://adoptium.net/temurin
  - Oracle JDK: https://www.oracle.com/java/technologies/downloads
- Git for Windows: https://git-scm.com/download/win
- IntelliJ IDEA Community (free): https://www.jetbrains.com/idea/download/

Tip: After installing, close and re-open your terminal/IDE so new PATH settings take effect.

---

## 2) Clone this repository in IntelliJ (recommended)
Use IntelliJ’s built-in Git support. This sets up the project and Gradle in one flow.

From the Welcome screen:
1) Click "Get from VCS" (or "New > Project from Version Control").
2) In the Repository URL field, paste:
   - https://github.com/PointerRain/BOTC-MC
3) Choose a local Directory for the project.
4) Click "Clone", then "Trust Project" when prompted.

From an already-open IntelliJ window:
1) VCS menu > "Get from Version Control…"
2) Paste the same URL and choose a Directory.
3) Click "Clone", then "Trust Project" when prompted.

What happens next (usually automatic):
- IntelliJ detects the Gradle build and loads the project.
- It uses the Gradle Wrapper (gradlew) automatically.
- It downloads dependencies and indexes the project (may take a few minutes).
- If IntelliJ needs anything (e.g., a JDK choice), it will prompt you. Accept the defaults or pick your installed JDK.

Optional checks (only if asked by IntelliJ):
- Gradle JVM: File > Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JVM. If prompted, select your installed JDK.
- Plugins: File > Settings > Plugins. Git and Gradle are bundled and usually enabled by default.

Open the Gradle tool window:
- View > Tool Windows > Gradle. You’ll run tasks like runServer and build from here. Use its search box to find tasks quickly.

---

## 3) Run the development server (first run will ask for EULA)
Run directly from IntelliJ.

IntelliJ IDEA
- Gradle tool window > search "runServer" > double-click runServer.
- The Run tool window will open and show the server console.

First run EULA prompt
- The server will stop and ask you to accept the EULA.

Accept the EULA
```powershell
# Open the EULA in Notepad, change eula=false to eula=true, save
notepad .\run\eula.txt
```
Then run the server again from the Gradle tool window (double-click runServer).

Tips
- Logs: `run/logs/latest.log`
- Server settings (like the port): `run/server.properties`
- Stop the server by typing `stop` in the console (Run tool window).

(Optional) Run from PowerShell
```powershell
./gradlew.bat runServer
```

---

## 4) Build the mod JAR
Run the build task from IntelliJ.

IntelliJ IDEA
- Gradle tool window > search "build" > double-click build under the build group.

When it finishes, your mod JAR will be in:
- `build/libs/` (pick the file without `-sources` or `-dev` in the name).

(Optional) Build from PowerShell
```powershell
./gradlew.bat build
```

---

## 5) Feature Branch Workflow (recommended)
A simple process to make and review changes using Git and GitHub.

1) Make sure you’re on the main branch and up to date
```powershell
git checkout main
git pull --rebase
```
2) Create a feature branch (name it after the change)
```powershell
git checkout -b feature/short-description
```
3) Make your changes in IntelliJ, then stage and commit
```powershell
git add .
git commit -m "feat: short description of the change"
```
4) Push your branch to GitHub
```powershell
git push -u origin feature/short-description
```
5) Open a Pull Request (PR)
- Go to your repository on GitHub. You’ll see a prompt to compare & create a PR.
- Add a clear description, link any issues, and submit the PR for review.

6) Keep your branch up to date (if main changes)
```powershell
git checkout main
git pull --rebase
git checkout feature/short-description
git rebase main
# If there are conflicts, resolve in IntelliJ, then:
git add .
git rebase --continue
```
7) After approval, merge the PR on GitHub. Delete the feature branch when done.

IDE tips for Git
- IntelliJ: View > Tool Windows > Git for history and changes. Use the Git toolbar for commit, branch, and push.
- Pull Requests: Tools > GitHub > Open In Browser (or install the GitHub plugin for in-IDE PRs if desired).

---

## 6) Gradle tips and troubleshooting
- Reload Gradle project
  - In the Gradle tool window, click the Refresh/Reload button.
- Refresh dependencies
```powershell
./gradlew.bat --refresh-dependencies
```
- Clean then rebuild
```powershell
./gradlew.bat clean build
```
- If Gradle sync fails
  - Use the Gradle "Reload/Refresh" action.
  - If prompted for a JDK, select your installed JDK.
  - If behind a proxy, configure proxy settings in Gradle/IDE.
- Java not found
  - Install a JDK, then restart IntelliJ.
- Port already in use when running the server
  - Stop other servers, or change `server-port` in `run/server.properties` to a free port.
- Gradle wrapper
  - Always use the included wrapper (gradlew). IntelliJ does this automatically when you import the project.

---

## 7) Handy PowerShell commands (from the project folder)
```powershell
# Run the dev server
./gradlew.bat runServer

# Build the mod
./gradlew.bat build

# Clean and rebuild
./gradlew.bat clean build

# Open EULA for editing
notepad .\run\eula.txt

# Open server properties
notepad .\run\server.properties
```

## 8) Where things are
- Source code: `src/main/java`
- Resources (mod metadata, assets): `src/main/resources`
- Built artifacts: `build/libs/`
- Dev server files (world, logs, configs): `run/`

---
If you get stuck, please open an issue and include your OS version, what you tried, and the last ~50 lines from `run/logs/latest.log` or the Gradle error output.
