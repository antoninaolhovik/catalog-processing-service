# Mentor guide: how to share the project step by step

## Recommended setup: two repositories

| Repo | Visibility | Content |
|---|---|---|
| `catalog-processing-service` (this repo) | **private** (mentor only) | full reference history, branch `step-1`, tags `step-N` (N ≠ 1) |
| `catalog-processing-service-task` | public or shared with the student, marked as a **GitHub template** | the initial Spring Initializr commit + `docs/TASK.md` only |

Publish the task repo from this one:

```bash
git switch -c task-template $(git rev-list --max-parents=0 step-1)
git checkout step-1 -- docs/TASK.md
git commit -m "Add task description"
git push <task-remote> task-template:main
git switch main && git branch -D task-template
```

The student creates their own repo from the template ("Use this template"). This way the student cannot read
the solutions of the next steps, and every student has a clean, separate history.

## Per-step loop

1. **Kick-off (15–30 min):** go through the step in TASK.md together, the competency it covers, and its "Done when" list.
2. **Student works** on branch `step-N` and opens a PR to their own `main`. The mentor is a reviewer.
3. **Review** the PR against the "Done when" list, then **compare** it with the reference:
   `git diff step-(N-1) step-N` (or GitHub compare `step-(N-1)...step-N`).
   Share only the parts that are useful as review comments, not the whole solution.
4. **After merge**, optionally show the reference diff for this step so the student can compare approaches.
   Steps 9 and 10 have no reference code yet. The best student solution can become the reference.
5. **If the student is stuck** or far behind, give them the reference state of the previous step as a
   starting point: `git archive step-N | tar -x -C <student-repo>` or a patch made with
   `git format-patch step-(N-1)..step-N`. For Step 1 use `git format-patch --root step-1`; for Step 2
   use `git diff step-1 step-2` (the `step-1` branch is not an ancestor of `step-2`).

## Tips

- Keep one GitHub issue per step in the student's repo (copy it from TASK.md), so progress and discussion stay
  linked to PRs.
- For the PeEx competency evidence, link the PR of each step next to the competency in the coverage table
  (TASK.md §4).
- Rename the Java package and artifact (`com.ymidianyi.marketplace...`) in the template, not in this repo,
  so the reference tags stay comparable to the original history.
