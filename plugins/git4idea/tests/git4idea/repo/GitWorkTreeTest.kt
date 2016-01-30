/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.repo

import com.intellij.openapi.vfs.LocalFileSystem
import git4idea.GitUtil
import git4idea.branch.GitBranchesCollection
import git4idea.config.GitVersion
import git4idea.test.GitExecutor.*
import git4idea.test.GitPlatformTest
import git4idea.test.GitTestUtil
import git4idea.test.GitTestUtil.initRepo
import org.junit.Assume.assumeTrue
import java.io.File

class GitWorkTreeTest : GitPlatformTest() {

  private lateinit var myMainRoot: String
  private lateinit var myRepo : GitRepository

  override fun setUp() {
    super.setUp()
    cd(myTestRoot)
    assumeTrue(GitVersion.parse(git("version")).isLaterOrEqual(GitVersion(2, 5, 0, 0)))

    val mainDir = File(myTestRoot, "main")
    assertTrue(mainDir.mkdir())
    myMainRoot = mainDir.path
    initRepo(myMainRoot, true)

    git("worktree add $myProjectPath")
    val gitDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(myProjectPath, GitUtil.DOT_GIT))
    assertNotNull(gitDir)
    myRepo = GitTestUtil.registerRepo(project, myProjectPath)
    assertEquals(1, myGitRepositoryManager.repositories.size)
    assertNotNull(myGitRepositoryManager.getRepositoryForRoot(myProjectRoot))
  }

  fun `test local branches`() {
    cd(myMainRoot)
    val masterHead = last()
    git("checkout -b feature")
    val featureHead = tac("f.txt")

    myRepo.update()

    val branches = myRepo.branches
    val expectedBranches = listOf("master", "feature", "project") // 'project' is created automatically by `git worktree add`
    assertSameElements(branches.localBranches.map {it.name}, expectedBranches)
    assertBranchHash(masterHead, branches, "master")
    assertBranchHash(featureHead, branches, "feature")
  }

  fun `test remote branches`() {
    cd(myTestRoot)
    git("clone --bare $myMainRoot parent.git")
    cd(myMainRoot)
    val parentPath = File(myTestRoot, "parent.git").path
    git("remote add origin $parentPath")
    git("push origin master")

    val masterHead = last()
    git("checkout -b feature")
    val featureHead = tac("f.txt")
    git("push origin feature")

    myRepo.update()

    val branches = myRepo.branches
    assertSameElements(branches.remoteBranches.map {it.nameForLocalOperations}, listOf("origin/master", "origin/feature"))
    assertBranchHash(masterHead, branches, "origin/master")
    assertBranchHash(featureHead, branches, "origin/feature")
  }

  fun `test HEAD`() {
    cd(myRepo)
    git("checkout -b feature")
    val featureHead = tac("f.txt")
    myRepo.update()

    assertEquals("Incorrect current branch", "feature", myRepo.currentBranchName)
    assertEquals("Incorrect current revision", featureHead, myRepo.currentRevision)
  }

  private fun assertBranchHash(expectedHash: String, branches: GitBranchesCollection, branchName: String) {
    assertEquals(expectedHash, branches.getHash(branches.findBranchByName(branchName)!!)!!.asString())
  }
}
