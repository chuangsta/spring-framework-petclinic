# Automated pull request reviews

`workflows/droid-review.yml` runs Factory Droid code and security reviews when a
pull request is opened, updated (`synchronize`), reopened, or marked ready for
review. Drafts are skipped. A push to a ready PR cancels an older in-progress
review for that PR so the latest diff is reviewed instead.

Keep `synchronize` enabled: without it, fixes pushed after the first review do
not get reviewed, and existing feedback can describe an obsolete commit.

## Prerequisites and trust boundary

- Install the Factory Droid GitHub App for this repository.
- Store a Factory API key in the Actions secret `FACTORY_API_KEY`. Never put its
  value in the workflow or logs.
- Preserve `automatic_review: true`, `automatic_security_review: true`, and the
  workflow's permissions, including `id-token: write` for GitHub App auth.
- Reviews use the action's default deep review preset.

Fork PRs do not receive repository secrets on `pull_request` events. Do not
switch to `pull_request_target` or run untrusted fork code with credentials to
work around that restriction.

## Verify generated review content

A successful Actions check is not proof of a review. In particular, the action
skips generation when the workflow differs from its version on the default
branch. This includes both the installation PR and a PR updating this workflow.
The run reports `Skipping action due to workflow validation` and may finish
successfully without posting any review. Do not bypass this validation.

After a human merges the workflow change, verify generation on a normal,
non-draft PR that does not change the review workflow.

With an authenticated GitHub CLI, substitute the PR number below:

```sh
REPO=chuangsta/spring-framework-petclinic
PR=123

# Record the latest head SHA and branch.
gh pr view "$PR" --repo "$REPO" --json headRefOid,headRefName

# Find the Droid Auto Review run for that branch and head SHA.
gh run list --repo "$REPO" --workflow droid-review.yml --limit 20 \
  --json databaseId,headBranch,headSha,event,status,conclusion,url

# Confirm a bot review was submitted, and compare commit_id with the head SHA.
gh api --paginate "repos/$REPO/pulls/$PR/reviews" \
  --jq '.[] | select(.user.login == "factory-droid[bot]") | {state, commit_id, body, html_url}'

# Inspect actual inline findings and the bot's review summary, not just checks.
gh api --paginate "repos/$REPO/pulls/$PR/comments" \
  --jq '.[] | select(.user.login == "factory-droid[bot]") | {commit_id, path, body, html_url}'
gh api --paginate "repos/$REPO/issues/$PR/comments" \
  --jq '.[] | select(.user.login == "factory-droid[bot]") | {body, html_url}'
```

For the summary comment, follow its job link and confirm that run belongs to the
current head SHA. A tracking message saying review is starting is not a completed
review. A completed review may report no findings; do not insert intentional
bugs or require spurious inline comments just to prove automation works.

To verify update coverage, push a genuine follow-up change to the same ready
PR. Confirm a new run and completed review for the new SHA. If no run starts,
check the `synchronize` trigger. If a run starts but produces no review, inspect
its logs for setup-only skips, missing secrets, GitHub App access, or errors.
