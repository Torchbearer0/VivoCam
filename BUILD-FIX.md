# Build fix

This version aligns Java and Kotlin compilation targets to JVM 17. It fixes the GitHub Actions error:

`Inconsistent JVM-target compatibility detected for tasks 'compileDebugJavaWithJavac' (1.8) and 'compileDebugKotlin' (17).`

Replace the files in your GitHub repository with this project's contents, commit, and push to rerun the Android CI workflow.
