package com.ddlab.gitpusher.bitbucket.core;

import com.ddlab.gitpusher.core.*;
import com.ddlab.gitpusher.exception.GenericGitPushException;
import com.ddlab.gitpusher.util.HTTPUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Set;

import static com.ddlab.gitpusher.util.CommonConstants.*;

public class BitBucketHandlerImpl implements IGitHandler {
  private UserAccount userAccount;

  public BitBucketHandlerImpl(UserAccount userAccount) {
    this.userAccount = userAccount;
  }

  @Override
  public String getUserName() throws Exception {
    String userName = null;
    String uri = BITBUCKET_API_URI + BITBUCKET_USER_API_URI;
    HttpGet httpGet = new HttpGet(uri);
    String encodedUser =
        HTTPUtil.getEncodedUser(userAccount.getUserName(), userAccount.getPassword());
    httpGet.setHeader("Authorization", "Basic " + encodedUser);
    try {
      GitResponse gitResponse = HTTPUtil.getHttpGetOrPostResponse(httpGet);
      if (gitResponse.getStatusCode().equals("401"))
        throw new GenericGitPushException("Invalid credentials");
      IResponseParser<String, String[]> responseParser = new BitBucketReponseParser();
      userName = (responseParser.getUser(gitResponse.getResponseText()))[0];
    } catch (GenericGitPushException e) {
      throw e;
    }
    return userName;
  }

  @Override
  public String[] getAllRepositories() throws Exception {
    String[] repoNames = null;
    String uri = BITBUCKET_API_URI + BITBUCKET_ALL_REPO_API_URI;
    String userName = getUserName();
    MessageFormat formatter = new MessageFormat(uri);
    uri = formatter.format(new String[] {userName});
    HttpGet httpGet = new HttpGet(uri);
    String encodedUser =
        HTTPUtil.getEncodedUser(userAccount.getUserName(), userAccount.getPassword());
    httpGet.setHeader("Authorization", "Basic " + encodedUser);
    try {
      GitResponse gitResponse = HTTPUtil.getHttpGetOrPostResponse(httpGet);
      if (gitResponse.getStatusCode().equals("401"))
        throw new GenericGitPushException("Invalid credentials");
      IResponseParser<String, String[]> responseParser = new BitBucketReponseParser();
      repoNames = responseParser.getAllRepos(gitResponse.getResponseText());
    } catch (GenericGitPushException e) {
      throw e;
    }
    return repoNames;
  }

  @Override
  public boolean repoExists(String repoName) throws Exception {
    boolean existsFlag = false;
    String uri = BITBUCKET_API_URI + BITBUCKET_EXISTING_REPO_API_URI;
    MessageFormat formatter = new MessageFormat(uri);
    String loginUser = getUserName();
    uri = formatter.format(new String[] {loginUser, repoName});
    HttpGet httpGet = new HttpGet(uri);
    String encodedUser =
        HTTPUtil.getEncodedUser(userAccount.getUserName(), userAccount.getPassword());
    httpGet.setHeader("Authorization", "Basic " + encodedUser);
    try {
      GitResponse gitResponse = HTTPUtil.getHttpGetOrPostResponse(httpGet);
      if (gitResponse.getStatusCode().equals("401"))
        throw new GenericGitPushException("Invalid credentials");
      existsFlag = gitResponse.getStatusCode().equals("200") ? true : false;
    } catch (GenericGitPushException e) {
      throw e;
    }
    return existsFlag;
  }

  @Override
  public String getUrlFromLocalRepsitory(File gitDirPath) throws Exception {
    String existingRepoUrl = null;
    Git git = null;
    Repository repository = null;
    try {
      git = Git.open(gitDirPath);
      repository = git.getRepository();
      existingRepoUrl = repository.getConfig().getString("remote", "origin", "url");
    } catch (IOException e) {
      throw new GenericGitPushException(
          "Unable to read the local .git directory to find out the Repo URL");
    }
    return existingRepoUrl;
  }

  @Override
  public boolean isGitDirAvailable(File gitDirPath) throws Exception {
    boolean isAvailable = false;
    Git git = null;
    try {
      git = Git.open(gitDirPath);
      isAvailable = true;
    } catch (IOException e) {
    } finally {
      if (git != null) git.close();
    }
    return isAvailable;
  }

  @Override
  public void createHostedRepo(String repoName) throws Exception {
    String loginUser = getUserName();
    String jsonRepo = new BitbucketHostedRepo().toJson();
    String uri = BITBUCKET_API_URI + BITBUCKET_CREATE_API_URI;
    MessageFormat formatter = new MessageFormat(uri);
    uri = formatter.format(new String[] {loginUser, repoName});
    HttpPost httpPost = new HttpPost(uri);
    String encodedUser =
        HTTPUtil.getEncodedUser(userAccount.getUserName(), userAccount.getPassword());
    httpPost.setHeader("Authorization", "Basic " + encodedUser);
    StringEntity jsonBodyRequest = new StringEntity(jsonRepo);
    httpPost.setEntity(jsonBodyRequest);
    httpPost.setHeader("Content-type", "application/json");
    try {
      GitResponse gitResponse = HTTPUtil.getHttpGetOrPostResponse(httpPost);
      if (gitResponse.getStatusCode().equals("401"))
        throw new GenericGitPushException("Invalid credentials");
      if (!gitResponse.getStatusCode().equals("200"))
        throw new GenericGitPushException(
            "Unable to create a repo, a repository may already exist.");
    } catch (GenericGitPushException e) {
      throw e;
    }
  }

  @Override
  public void clone(String repoName, File dirPath) throws Exception {
    String uri = BITBUCKET_CLONE_API_URI;
    String loginUser = getUserName();
    MessageFormat formatter = new MessageFormat(uri);
    uri = formatter.format(new String[] {loginUser, loginUser, repoName});
    System.out.println("Git clone URI : " + uri);
    CredentialsProvider crProvider =
        new UsernamePasswordCredentialsProvider(
            userAccount.getUserName(), userAccount.getPassword());
    Git git = null;
    try {
      git =
          Git.cloneRepository()
              .setCredentialsProvider(crProvider)
              .setURI(uri)
              .setDirectory(dirPath)
              .call();
    } catch (GitAPIException e) {
      throw new GenericGitPushException("Unable to clone the project from GitHub...");
    } finally {
      if (git != null) git.close();
    }
  }

  @Override
  public void update(File cloneDirPath, String message) throws Exception {
    Git git = null;
    RevCommit revisionCommit = null;
    String comitMsg = GENERIC_COMIT_MSG;
    MessageFormat formatter = new MessageFormat(comitMsg);
    String userName = getUserName();
    userName = (userName == null) ? userAccount.getUserName() : userName;
    PersonIdent authorDetails = new PersonIdent(userName, userAccount.getUserName());
    message =
        (message == null) ? formatter.format(new String[] {userAccount.getUserName()}) : message;
    try {
      git = Git.open(cloneDirPath);
      git.add().addFilepattern(".").call();
      CommitCommand commitCommand =
          git.commit()
              .setAuthor(authorDetails)
              .setCommitter(authorDetails)
              .setAll(true)
              .setMessage(message);
      revisionCommit = commitCommand.call();

      System.out.println("All the files have been added and committed successfully");
      System.out.println("Going to push the files.....");

      CredentialsProvider cp =
          new UsernamePasswordCredentialsProvider(
              userAccount.getUserName(), userAccount.getPassword());
      PushCommand pushCommand = git.push();
      pushCommand.setCredentialsProvider(cp).setForce(true).setPushAll();

      Set<RemoteRefUpdate.Status> statusSet = new HashSet<RemoteRefUpdate.Status>();
      Iterable<PushResult> resultIterable = pushCommand.call();
      PushResult pushResult = resultIterable.iterator().next();
      for (final RemoteRefUpdate rru : pushResult.getRemoteUpdates()) {
        RemoteRefUpdate.Status status = rru.getStatus();
        if (status != RemoteRefUpdate.Status.OK && status != RemoteRefUpdate.Status.UP_TO_DATE) {
          // Do something
          System.out.println("Something is wrong ....");
        }
        statusSet.add(status);
      }
      if (statusSet.contains(RemoteRefUpdate.Status.OK)) {
        System.out.println("All files pushed to GitHub successfully");
      }
    } catch (IOException | GitAPIException e) {
      revertChanges(git, revisionCommit);
      throw new GenericGitPushException("" + e.getMessage());
    } finally {
      if (git != null) git.close();
    }
  }

  private void revertChanges(Git git, RevCommit revisionCommit) {
    System.out.println("Going to revert the changes");
    try {
      git.revert().call();
      //      git.revert().include(revisionCommit).call();
      //      git.reset().setMode(ResetCommand.ResetType.HARD).call();

    } catch (GitAPIException e) {
      e.printStackTrace();
    }
  }

  @Override
  public String[] getGists() throws Exception {
    String[] gistSnippets = null;
    String uri = BITBUCKET_API_URI + BITBUCKET_GET_OR_CREATE_GIST_API_URI;
    String loginUser = getUserName();
    MessageFormat formatter = new MessageFormat(uri);
    uri = formatter.format(new String[] {loginUser});
    System.out.println("Gist URI : " + uri);

    HttpGet httpGet = new HttpGet(uri);
    String encodedUser =
        HTTPUtil.getEncodedUser(userAccount.getUserName(), userAccount.getPassword());
    httpGet.setHeader("Authorization", "Basic " + encodedUser);
    try {
      GitResponse gitResponse = HTTPUtil.getHttpGetOrPostResponse(httpGet);
      if (gitResponse.getStatusCode().equals("401"))
        throw new GenericGitPushException("Invalid credentials");
      IResponseParser<String, String[]> responseParser = new BitBucketReponseParser();
      gistSnippets = responseParser.getAllGistSnippets(gitResponse.getResponseText());
    } catch (GenericGitPushException e) {
      throw e;
    }
    return gistSnippets;
  }

  @Override
  public void createGist(File file, String description) throws Exception {
    String uri = BITBUCKET_API_URI + BITBUCKET_GET_OR_CREATE_GIST_API_URI;
    String loginUser = getUserName();
    MessageFormat formatter = new MessageFormat(uri);
    uri = formatter.format(new String[] {loginUser});
    System.out.println("Gist URI : " + uri);

    HttpPost httpPost = new HttpPost(uri);
    String encodedUser =
        HTTPUtil.getEncodedUser(userAccount.getUserName(), userAccount.getPassword());
    httpPost.setHeader("Authorization", "Basic " + encodedUser);

    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    builder.addBinaryBody("file", file);
    builder.addTextBody("title", description);
    httpPost.setEntity(builder.build());

    try {
      GitResponse gitResponse = HTTPUtil.getHttpGetOrPostResponse(httpPost);
        if (gitResponse.getStatusCode().equals("401"))
            throw new GenericGitPushException("Invalid credentials");
      if (!gitResponse.getStatusCode().equals("201"))
        throw new GenericGitPushException("Unable to create a Snippet");
    } catch (GenericGitPushException e) {
      throw e;
    }
  }

  private static class BitbucketHostedRepo {
    @JsonProperty("scm")
    private String scm = "git";

    public String getScm() {
      return scm;
    }

    public void setScm(String scm) {
      this.scm = scm;
    }

    public String toJson() {
      ObjectMapper mapper = new ObjectMapper();
      String toJson = null;
      try {
        toJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this);
      } catch (IOException e) {
        e.printStackTrace();
      }
      return toJson;
    }
  }
}
