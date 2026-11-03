const { readFile } = require('node:fs/promises');

function validatePostResults(results) {
  const counts = ['posted', 'fallbackPosted', 'approved', 'failed', 'skipped'];
  if (!results || counts.some((field) =>
    !Number.isSafeInteger(results[field]) || results[field] < 0)) {
    throw new Error('Droid review post-results contain invalid counts.');
  }
  if (!Array.isArray(results.failures) || results.failures.length > 0 ||
      results.failed > 0 || results.skipped > 0 ||
      results.posted + results.fallbackPosted !== results.approved) {
    throw new Error('Droid did not deliver all validated review findings.');
  }
}

async function verifyReview({ github, context, resultsPath }) {
  let results;
  try {
    results = JSON.parse(await readFile(resultsPath, 'utf8'));
  } catch {
    throw new Error(
      'Droid review post-results are missing or invalid. Review generation may ' +
      'have been skipped during workflow setup or candidate validation. See .github/README.md.'
    );
  }
  validatePostResults(results);

  const comments = await github.paginate(github.rest.issues.listComments, {
    ...context.repo,
    issue_number: context.issue.number,
    per_page: 100
  });
  const runUrl = `${context.serverUrl}/${context.repo.owner}/${context.repo.repo}/actions/runs/${context.runId}`;
  const completed = comments.some(({ user, body }) =>
    user?.login === 'factory-droid[bot]' && user.type === 'Bot' &&
    typeof body === 'string' && body.startsWith('**Droid finished ') &&
    body.includes(`[View job](${runUrl})`) &&
    /<!-- factory-pr-issue-comment: run-type=droid-review(?:-and-security)? -->/.test(body)
  );
  if (!completed) {
    throw new Error('No completed Droid review comment was delivered for this workflow run.');
  }
}

module.exports = { validatePostResults, verifyReview };
