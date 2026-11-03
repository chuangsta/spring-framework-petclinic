const assert = require('node:assert/strict');
const { mkdtemp, writeFile, rm } = require('node:fs/promises');
const { tmpdir } = require('node:os');
const { join } = require('node:path');
const { test } = require('node:test');
const { validatePostResults, verifyReview } = require('./verify-droid-review.cjs');

const results = {
  posted: 1, fallbackPosted: 0, approved: 1, failed: 0, skipped: 0, failures: []
};
const context = {
  repo: { owner: 'example', repo: 'petclinic' },
  issue: { number: 3 }, serverUrl: 'https://github.com', runId: 123
};
const comment = {
  user: { login: 'factory-droid[bot]', type: 'Bot' },
  body: "**Droid finished @maintainer's task** " +
    '[View job](https://github.com/example/petclinic/actions/runs/123)\n\n' +
    'Review summary: one actionable finding.\n\n' +
    '<!-- factory-pr-issue-comment: run-type=droid-review-and-security -->'
};

async function fixture(t, comments = [comment], output = results) {
  const directory = await mkdtemp(join(tmpdir(), 'droid-review-test-'));
  t.after(() => rm(directory, { recursive: true, force: true }));
  const resultsPath = join(directory, 'review_post_results.json');
  if (output !== undefined) {
    await writeFile(resultsPath, JSON.stringify(output));
  }
  const listComments = () => {};
  return {
    context, resultsPath,
    github: {
      rest: { issues: { listComments } },
      paginate: async (method, params) => {
        assert.equal(method, listComments);
        assert.deepEqual(params, {
          owner: 'example', repo: 'petclinic', issue_number: 3, per_page: 100
        });
        return comments;
      }
    }
  };
}

test('accepts a completed review with a comment for the current run', async (t) => {
  await verifyReview(await fixture(t));
});

test('accepts review-body fallback findings', () => {
  validatePostResults({ ...results, posted: 0, fallbackPosted: 1 });
});

test('accepts a completed review with no findings without inventing comments', async (t) => {
  await verifyReview(await fixture(t, [comment], {
    ...results, posted: 0, approved: 0, summaryBody: 'No actionable findings.'
  }));
});

test('rejects setup-only runs even when old bot comments exist', async (t) => {
  const options = await fixture(t);
  await rm(options.resultsPath);
  await assert.rejects(verifyReview(options), /post-results are missing or invalid/);
});

test('rejects malformed output without printing its contents', async (t) => {
  const options = await fixture(t);
  await writeFile(options.resultsPath, 'not valid JSON');
  await assert.rejects(verifyReview(options), /post-results are missing or invalid/);
});

for (const invalid of [null, [], {}, { ...results, posted: '1' },
  { ...results, approved: -1 }, { ...results, posted: 0.5 }]) {
  test(`rejects invalid post-result counts: ${JSON.stringify(invalid)}`, () => {
    assert.throws(() => validatePostResults(invalid), /invalid counts/);
  });
}

for (const incomplete of [
  { ...results, posted: 0 },
  { ...results, failed: 1 },
  { ...results, skipped: 1 },
  { ...results, failures: [{}] },
  { ...results, failures: null }
]) {
  test(`rejects undelivered findings: ${JSON.stringify(incomplete)}`, () => {
    assert.throws(() => validatePostResults(incomplete), /did not deliver/);
  });
}

for (const [name, replacement] of [
  ['no comment', null],
  ['human copying the bot message', { user: { login: 'maintainer', type: 'User' } }],
  ['unrelated bot', { user: { login: 'other[bot]', type: 'Bot' } }],
  ['previous run', { body: comment.body.replace('/runs/123)', '/runs/122)') }],
  ['run ID prefix collision', { body: comment.body.replace('/runs/123)', '/runs/1234)') }],
  ['error', { body: comment.body.replace('Droid finished ', 'Droid encountered an error ') }],
  ['still running', { body: comment.body.replace('Droid finished ', 'Droid is reviewing code ') }],
  ['non-review task', { body: comment.body.replace('droid-review-and-security', 'droid-fill') }],
  ['empty body', { body: null }]
]) {
  test(`rejects ${name} as proof of review delivery`, async (t) => {
    const comments = replacement ? [{ ...comment, ...replacement }] : [];
    await assert.rejects(verifyReview(await fixture(t, comments)), /No completed Droid review/);
  });
}

test('finds the current review among paginated comments', async (t) => {
  await verifyReview(await fixture(t, [
    ...Array.from({ length: 100 }, () => ({ user: null, body: 'Older discussion' })),
    comment
  ]));
});

test('does not turn API permission failures into success', async (t) => {
  const options = await fixture(t);
  options.github.paginate = async () => { throw new Error('HTTP 403'); };
  await assert.rejects(verifyReview(options), /HTTP 403/);
});
