# Welcome! Thank you for contributing to Apache Pekko gRPC!

We follow the standard GitHub [fork & pull](https://help.github.com/articles/using-pull-requests/#fork--pull) approach to pull requests. Just fork the official repo, develop in a branch, and submit a PR!

You're always welcome to submit your PR straight away and start the discussion (without reading the rest of this wonderful doc, or the README.md). The goal of these notes is to make your experience contributing to Pekko gRPC as smooth and pleasant as possible. We're happy to guide you through the process once you've submitted your PR.

# The Apache Pekko Community

If you have questions about the contribution process or discuss specific issues, please interact with the community using the following resources.

- [GitHub discussions](https://github.com/apache/pekko-grpc/discussions): for questions and general discussion.
- [Pekko dev mailing list](https://lists.apache.org/list.html?dev@pekko.apache.org): for Pekko development discussions.
- [GitHub issues](https://github.com/apache/pekko-grpc/issues): for bug reports and feature requests. Please search the existing issues before creating new ones. If you are unsure whether you have found a bug, consider asking in GitHub discussions or the mailing list first.

# Navigating around the project & codebase

## Branches summary

Depending on which version (or sometimes module) you want to work on, you should target a specific branch as explained below:

* `main` – active development branch of pekko-grpc

## Issues

In general *all issues are open for anyone working on them*, however if you're new to the project and looking for an issue
that will be accepted and likely is a nice one to get started you should check out the following tags:

- [help wanted](https://github.com/apache/pekko-grpc/labels/help%20wanted) - which identifies issues that the core team will likely not have time to work on, or the issue is a nice entry level ticket. If you're not sure how to solve a ticket but would like to work on it feel free to ask in the issue about clarification or tips.
- [nice-to-have (low-priority)](https://github.com/apache/pekko-grpc/labels/nice-to-have%20%28low-prio%29) - are tasks which make sense, however are not very high priority (in face of other very high priority issues). If you see something interesting in this list, a contribution would be really wonderful!

Another group of special tags indicate specific states a ticket is in:

- [bug](https://github.com/apache/pekko-grpc/labels/bug) - bugs take priority in being fixed above features. The core team dedicates a number of days to working on bugs each sprint. Bugs which have reproducers are also great for community contributions as they're well isolated. Sometimes we're not as lucky to have reproducers though, then a bugfix should also include a test reproducing the original error along with the fix.
- [failed](https://github.com/apache/pekko-grpc/labels/failed) - tickets indicate a Jenkins failure (for example from a nightly build). These tickets usually start with the `FAILED: ...` message, and include a stacktrace + link to the Jenkins failure. The tickets are collected and worked on with priority to keep the build stable and healthy. Often times it may be simple timeout issues (Jenkins boxes are slow), though sometimes real bugs are discovered this way.

Pull Request validation states:

- `validating => [tested | needs-attention]` - signify pull request validation status

## Running the tests

gRPC runs on HTTP/2 and connections commonly use HTTPS.

If you are running on JDK 8, you will need to use at least version 1.8.0u251 to make sure ALPN protocol negotiation is available.  

# Pekko gRPC contributing guidelines

These guidelines apply to all Pekko projects, by which we mean both the `apache/pekko` repository,
as well as any plugins or additional repos located under the Apache Pekko GitHub organisation, e.g. `apache/pekko-grpc` and others.

These guidelines are meant to be a living document that should be changed and adapted as needed.
We encourage changes that make it easier to achieve our goals in an efficient way.

Please also note that we have a *Code of Conduct* in place which aims keep our community a nice and helpful one.
You can read its full text here: [ASF Code of Conduct](https://www.apache.org/foundation/policies/conduct).

## General Workflow

The below steps are how to get a patch into a main development branch (e.g. `main`).
The steps are exactly the same for everyone involved in the project (be it core team, or first time contributor).

1. To avoid duplicated effort, it might be good to check the [issue tracker](https://github.com/apache/pekko-grpc/issues) and [existing pull requests](https://github.com/apache/pekko-grpc/pulls) for existing work.
   - If there is no ticket yet, feel free to [create one](https://github.com/apache/pekko-grpc/issues/new) to discuss the problem and the approach you want to take to solve it.
1. [Fork the project](https://github.com/apache/pekko-grpc#fork-destination-box) on GitHub. You'll need to create a feature-branch for your work on your fork, as this way you'll be able to submit a PullRequest against the mainline Pekko-gRPC.
1. Create a branch on your fork and work on the feature. For example: `git checkout -b wip-custom-headers-pekko-grpc`
   - Please make sure to follow the general quality guidelines (specified below) when developing your patch.
   - Please write additional tests covering your feature and adjust existing ones if needed before submitting your Pull Request. The `validatePullRequest` sbt task ([explained below](#the-validatepullrequest-task)) may come in handy to verify your changes are correct.
1. Once your feature is complete, prepare the commit following our [Creating Commits And Writing Commit Messages](#creating-commits-and-writing-commit-messages). For example, a good commit message would be: `Adding compression support for Manifests #22222` (note the reference to the ticket it aimed to resolve).
1. Now it's finally time to [submit the Pull Request](https://help.github.com/articles/using-pull-requests)!
1. For large PRs, we may ask you to submit an Apache Software Foundation [CLA](https://www.apache.org/licenses/contributor-agreements.html).
1. Now both committers and interested people will review your code. This process is to ensure the code we merge is of the best possible quality, and that no silly mistakes slip through. You're expected to follow-up these comments by adding new commits to the same branch. The commit messages of those commits can be more lose, for example: `Removed debugging using printline`, as they all will be squashed into one commit before merging into the main branch.
    - The community and team are really nice people, so don't be afraid to ask follow up questions if you didn't understand some comment, or would like to clarify how to continue with a given feature. We're here to help, so feel free to ask and discuss any kind of questions you might have during review!
1. After the review you should fix the issues as needed (pushing a new commit for new review etc.), iterating until the reviewers give their thumbs up–which is signalled usually by a comment saying `LGTM`, which means "Looks Good To Me". 
    - In general a PR is expected to get 2 LGTMs from the team before it is merged. If the PR is trivial, or under special circumstances (such as most of the team being on vacation, a PR was very thoroughly reviewed/tested and surely is correct) one LGTM may be fine as well.
1. If the code change needs to be applied to other branches as well (for example a bugfix needing to be backported to a previous version), one of the team will either ask you to submit a PR with the same commit to the old branch, or do this for you.
   - Backport pull requests such as these are marked using the phrase`for validation` in the title to make the purpose clear in the pull request list. They can be merged once validation passes without additional review (if no conflicts).
1. Once everything is said and done, your Pull Request gets merged :tada: Your feature will be available with the next “earliest” release milestone (i.e. if back-ported so that it will be in release x.y.z, find the relevant milestone for that release). And of course you will be given credit for the fix in the release stats during the release's announcement. You've made it!

The TL;DR; of the above very precise workflow version is:

1. Fork pekko-grpc
2. Hack and test on your feature (on a branch)
3. Submit a PR
4. Sign the CLA if necessary
4. Keep polishing it until received enough LGTM
5. Profit!

Note that the pekko-grpc sbt project is not as large as the Pekko one, so `sbt` should be able to run with less heap than with the Pekko project. In case you need to increase the heap, this can be specified using a command line argument `sbt -mem 2048` or in the environment variable `SBT_OPTS` but then as a regular JVM memory flag, for example `SBT_OPTS=-Xmx2G`, on some platforms you can also edit the global defaults for sbt in `/usr/local/etc/sbtopts`.

## Binary compatibility

Pekko gRPC is still in experimental mode, so this section does not apply yet.
However, once we declare the project stable, we will adhere to the following
rules:

Our binary compatibility guarantees are described in depth in the
[Binary Compatibility](https://pekko.apache.org/docs/pekko-grpc/current/binary-compatibility.html)
section of the documentation.

Pekko gRPC will use [Lightbend MiMa](https://github.com/lightbend/mima) to
validate binary compatibility of incoming Pull Requests after we get v1.0.0 released.

If you get a MiMa failure, it's good to consult with a core team member if the violation can be safely ignored, or if it would
indeed break binary compatibility in a problematic way.  Situations when it may be fine to ignore a MiMa issued warning include:

- if it is touching any class marked as `private[pekko]`, `/** INTERNAL API*/`, `@InternalApi`, `@ApiMayChange` or similar markers
- if it is concerning internal classes (often recognisable by package names like `dungeon`, `impl`, `internal` etc.)
- if it is adding API to classes / traits which are only meant for extension within the library itself, i.e. should not be extended by end-users
- other tricky situations

If it turns out that the change can be safely ignored, please add the filter to a new file in the submodule's `src/main/mima-filters/<last-released-version>.backwards.excludes` directory.

You can run `mimaReportBinaryIssues` on the sbt console to check if you introduced a binary incompatibility or whether an
incompatibility has been successfully ignored after adding it to the filter file.

### Generated code

Generated code is not checked by MiMa. However, we do want code generated with a previous version of Pekko gRPC
to be usable with later versions. This means you should be extra careful not to introduce binary incompatibilities
when changing the code generators. Of course, when adding new Pekko gRPC features it is not required that those
features work with code generated for previous versions.

Generated code may call Pekko gRPC code that is internal. In those cases we mark those internal API's as
`@InternalStableApi` to avoid accidentally breaking compatibility.

## Pull Request Requirements

For a Pull Request to be considered at all it has to meet these requirements:

1. Regardless if the code introduces new features or fixes bugs or regressions, it must have comprehensive tests.
1. The code must be well documented in the Lightbend's standard documentation format (see the ‘Documentation’ section below).
1. The commit messages must properly describe the changes, see further below.
1. All new source files should have [Apache source headers](https://www.apache.org/legal/src-headers.html).

### Additional guidelines

Some additional guidelines regarding source code are:

- keep the code [DRY](https://en.wikipedia.org/wiki/Don%27t_repeat_yourself)
- apply the [Boy Scout Rule](https://www.oreilly.com/library/view/97-things-every/9780596809515/ch08.html) whenever you have the chance to
- Never delete or change existing copyright notices, just add additional info.  
- Do not use ``@author`` tags since it does not encourage [Collective Code Ownership](http://www.extremeprogramming.org/rules/collective.html).
  - Contributors , each project should make sure that the contributors gets the credit they deserve—in a text file or page on the project website and in the release notes etc.

If these requirements are not met then the code should **not** be merged into main, or even reviewed - regardless of how good or important it is. No exceptions.

Whether or not a pull request (or parts of it) shall be back- or forward-ported will be discussed on the pull request discussion page, it shall therefore not be part of the commit messages. If desired the intent can be expressed in the pull request description.

## Documentation

All documentation must abide by the following maxims:

- Example code should be run as part of an automated test suite.
- Version should be **programmatically** specifiable to the build.
- Generation should be **completely automated** and available for scripting.
- When renaming Markdown files, add a rewrite rule to the `.htaccess` file to not break external links.

All documentation is preferred to be in Lightbend's standard documentation format [Paradox](https://github.com/lightbend/paradox).
The language used by Paradox is a super-set or Markdown which supports most Github Flavored Markdown extensions as well as additional directives to facilitate writing documentation for software projects.
Refer to its documentation to learn about the more advanced features it provides (including code etc).

To generate documentation you can:

```
> project docs
> paradox
```

The rendered documentation will be available under `docs/target/paradox/site/main/index.html`.

### JavaDoc

Pekko gRPC generates JavaDoc-style API documentation using the [genjavadoc](https://github.com/lightbend/genjavadoc) sbt plugin, since the sources are written mostly in Scala.

Generating JavaDoc is not enabled by default, as it's not needed on day-to-day development as it's expected to just work.
If you'd like to check if you links and formatting looks good in JavaDoc (and not only in ScalaDoc), you can generate it by running:

```
sbt -Dpekko.genjavadoc.enabled=true javaunidoc:doc
```

Which will generate JavaDoc style docs in `./target/javaunidoc/index.html`

## External Dependencies

All the external runtime dependencies for the project, including transitive dependencies, must have an open source license that is equal to, or compatible with, [Apache 2](https://www.apache.org/licenses/LICENSE-2.0).

This must be ensured by manually verifying the license for all the dependencies for the project:

1. Whenever a committer to the project changes a version of a dependency (including Scala) in the build file.
2. Whenever a committer to the project adds a new dependency.
3. Whenever a new release is cut (public or private for a customer).

Which licenses are compatible with Apache 2 are defined in [this doc](https://www.apache.org/legal/resolved.html#category-a), where you can see that the licenses that are listed under ``Category A`` automatically compatible with Apache 2, while the ones listed under ``Category B`` needs additional action:

> Each license in this category requires some degree of reciprocity. This may mean that additional action is warranted in order to minimize the chance that a user of an Apache product will create a derivative work of a differently-licensed portion of an Apache product without being aware of the applicable requirements.

Each project must also create and maintain a list of all dependencies and their licenses, including all their transitive dependencies. This can be done either in the documentation or in the build file next to each dependency.

## Creating Commits And Writing Commit Messages

Follow these guidelines when creating public commits and writing commit messages.

1. First line should be a descriptive sentence what the commit is doing, including the ticket number. It should be possible to fully understand what the commit does—but not necessarily how it does it—by just reading this single line. We follow the “imperative present tense” style for commit messages ([more info here](https://tbaggery.com/2008/04/19/a-note-about-git-commit-messages.html)).

   It is **not ok** to only list the ticket number, type "minor fix" or similar.
   If the commit is a small fix, then you are done. If not, go to 2.

2. Following the single line description should be a blank line followed by an enumerated list with the details of the commit.

3. You can request review by a specific team member  for your commit (depending on the degree of automation we reach, the list may change over time):
    * ``Review by @gituser`` - if you want to notify someone on the team. The others can, and are encouraged to participate.

Example:

    enable CI #1

    * Details 1
    * Details 2
    * Details 3

### Ignoring formatting commits in git blame

Throughout the history of the codebase various formatting commits have been applied as the scalafmt style has evolved over time, if desired
one can setup git blame to ignore these commits. The hashes for these specific are stored in [this file](.git-blame-ignore-revs) so to configure
git blame to ignore these commits you can execute the following.

```shell
git config blame.ignoreRevsFile .git-blame-ignore-revs
```

## Source style

### Scala style 

Pekko gRPC uses [scalafmt](https://scalameta.org/scalafmt) to enforce some of the code style rules.

### Java style

Java code is currently not automatically reformatted by sbt (expecting to have a plugin to do this soon).
Thus we ask Java contributions to follow these simple guidelines:

- 2 spaces
- `{` on same line as method name
- in all other aspects, follow the [Oracle Java Style Guide](https://www.oracle.com/technetwork/java/codeconvtoc-136057.html)

### Preferred ways to use timeouts in tests

Avoid short test timeouts, since Jenkins server may GC heavily causing spurious test failures. GC pause or other hiccup of 2 seconds is common in our CI environment. Please note that usually giving a larger timeout *does not slow down the tests*, as in an `expectMessage` call for example it usually will complete quickly.

There is a number of ways timeouts can be defined in Pekko tests. The following ways to use timeouts are recommended (in order of preference): 

* `remaining` is first choice (requires `within` block)
* `remainingOrDefault` is second choice
* `3.seconds` is third choice if not using testkit
* lower timeouts must come with a very good reason (e.g. Awaiting on a known to be "already completed" `Future`)

Special care should be given `expectNoMessage` calls, which indeed will wait the entire timeout before continuing, therefore a shorter timeout should be used in those, for example `200` or `300.millis`.

You can read up on remaining and friends in [TestKit.scala](https://github.com/apache/pekko/blob/main/testkit/src/main/scala/org/apache/pekko/testkit/TestKit.scala)

## Testing

As a rule contributions should be accompanied by tests.

### interop-tests

The `interop-tests` subproject holds the `org.apache.pekko.grpc.interop.GrpcInteropTests`,
which runs a selection of the tests from https://github.com/grpc/grpc-web/blob/master/doc/interop-test-descriptions.md
between various variations of Pekko gRPC and the grpc-java implementation.

This subproject also holds tests for the `codegen` subproject that want to test the actually generated code.
Such tests are not in the `codegen` project because otherwise it would create an almost
circular dependency, where the codegen test classes cannot be compiled before the generated
classes have been created, which requires the codegen main classes to have been compiled.

Alternatively, you can create a `scripted` test under `sbt-plugin` for a more self-contained testcase.

# Supporting infrastructure

## Continuous Integration

pekko-grpc currently uses Github Actions for Continuous Integration. See the `Checks` tab in a PR for details about the current run.

## Related links

* [Apache Contributor License Agreement](https://www.apache.org/licenses/contributor-agreements.html)
* [Pekko gRPC Issue Tracker](https://github.com/apache/pekko-grpc/issues)
