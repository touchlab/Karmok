# Release Checklist
- [ ] [Update Plugin Version](https://github.com/touchlab/Karmok/blob/d3d00d60ac6fde61032f46ffd5334444881b21cc/idea-plugin/build.gradle#L6)
- [ ] Build Plugin `./gradlew clean buildPlugin`
- [ ] Get plugin zip from: <karmok location>/idea-plugin/build/distributions
- [ ] Create a [new release](https://github.com/touchlab/Karmok/releases/new) on github and attach the zip as a binary
- [ ] [Update Library Version](https://github.com/touchlab/Karmok/blob/9d65d4644f0ffdf80a9b8a0a3c4cb91a5b6528c5/gradle.properties#L4)
- [ ] Configure signing and upload credentials for publishing library to Sonatype.
- [ ] Upload library with `./gradlew :karmok-library:publish`
- [ ] Close and release on [Sonatype](https://oss.sonatype.org/#stagingRepositories)
- [ ] Update installation instructions in [README](README.md)
