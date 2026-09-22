# ticketmasta

![Build](https://github.com/ZeroQLi/ticketmasta/workflows/Build/badge.svg)

**ticketmasta** is an IntelliJ Platform plugin that lets you work on your repository's GitHub
issues without leaving the IDE.

## Features

- **GitHub issues panel** — lists the open issues of your repository in a right-side tool window,
  including private repositories (authenticated with a personal access token). Repository name
  can be set explicitly or auto-detected from the project's `.git/config`.
- **Markdown issue overview** — the selected issue renders as compact, theme-aware Markdown with
  a link back to GitHub.
- **Vditor editor** — an embedded Markdown editor for your working notes.
- **Scaffold AI** — diagnoses the selected issue against a digest of your codebase and drafts a
  `TODO.md` with an actionable task list.
- **Send to AI** — saves the editor to `TODO.md` and launches an [opencode](https://opencode.ai) agent
  session in the built-in Terminal, asking it to work through the checklist and resolve the selected issue.
- **Save** — writes the editor content to `TODO.md` in the project root.

## Installation

- From JetBrains Marketplace:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "ticketmasta"</kbd> > <kbd>Install</kbd>

- From disk:

  Download the latest release from [GitHub Releases](https://github.com/ZeroQLi/ticketmasta/releases/latest) and install it via
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

## Configuration

Open <kbd>Settings/Preferences</kbd> > <kbd>Tools</kbd> > <kbd>TicketMasta</kbd>:

| Setting | Description |
| --- | --- |
| GitHub repository | `owner/repo`, or leave empty to auto-detect from the project's git config |
| GitHub token | Personal access token (stored in the IDE PasswordSafe); needs `repo` scope for private repositories |
| AI provider | `openai` or `openrouter` |
| AI base URL | Auto-filled from the provider (`https://api.openai.com/v1` / `https://openrouter.ai/api/v1`) |
| AI API key | Key for the chosen provider |
| AI model | Model identifier for the chosen provider |
| Agent command | Command template run in the built-in Terminal by **Send to AI**; placeholders `{prompt}`, `{file}`, `{number}`, `{title}`, `{repo}` |

Use **Test connection** to verify the repository and token before you start.

### Send to AI

**Send to AI** writes the editor to `TODO.md` in the project root and runs the **Agent command** in a
new built-in Terminal tab. The default command is:

```shell
opencode run --file TODO.md --title "{title}" "{prompt}"
```

so the [OpenCode CLI](https://opencode.ai) must be on your `PATH` and authenticated
(`opencode auth login`).

### Prompt files

Prompts are plain Markdown files stored globally under the IDE config directory, so you can edit them
freely (use the **Open** / **Open prompts folder** buttons in Settings):

| File | Used by |
| --- | --- |
| `<IDE config>/ticketmasta/prompts/scaffold.md` | **Scaffold AI** system prompt |
| `<IDE config>/ticketmasta/prompts/agent.md` | **Send to AI** prompt; supports `{file}`, `{number}`, `{title}`, `{repo}` |

## Development

Run the plugin in a development sandbox:

```shell
./gradlew runIde
```

---

Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
