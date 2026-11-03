# Automated pull request reviews

`workflows/droid-review.yml` runs Factory Droid's deep code review and security
review when a non-draft PR is opened, reopened, marked ready, or updated with new
commits (`synchronize`). New commits cancel an older in-progress review of the
same PR. Draft PRs wait until they are marked ready.

## Required setup

- Install the [Factory Droid GitHub App](https://app.factory.ai/settings/integrations/github/start)
  for this repository.
- Store a Factory API key as the Actions secret `FACTORY_API_KEY`. Never commit
  the key. The workflow uses OIDC (`id-token: write`) for the app's GitHub access.
- Merge workflow changes through a PR into the default branch, `mission-demo`.
  Do not push directly to `master` or `main`.

## What a passing review means

After Droid runs, the workflow checks its `review_post_results.json` to confirm
the validation/posting stage completed and all approved findings were delivered,
either inline or in the review body. It then reads PR comments with the workflow's
own GitHub token, because Droid revokes its app token when its action finishes.
The check requires a completed review comment authored by `factory-droid[bot]`
and linked to this exact workflow run. Old comments, in-progress messages, error
messages, and successful status checks alone do not count. Reviews with no
findings are valid; the workflow never invents findings or posts placeholder
comments.

Factory can deliberately skip review generation when adding or changing the
review workflow before it is trusted on the default branch. Previously this
could produce a green setup-only run. The verification step now fails instead.
After a human merges the workflow, verify it on the next non-draft PR or new
commit. Do not bypass Factory's workflow validation or switch to
`pull_request_target` to make a workflow-changing PR pass.

For a failure, inspect the Droid step for workflow-validation, authentication,
missing candidate output, or posting errors. Confirm the app installation and
secret configuration if authentication fails. For fork PRs, GitHub does not
provide repository secrets to `pull_request` jobs; a maintainer must arrange a
trusted review rather than expose secrets to fork code.

Run the verification regression tests locally with Node.js 20 or later:

```sh
node --test .github/scripts/verify-droid-review.test.cjs
```
