buildMvn {
  publishModDescriptor = false
  publishAPI = false
  mvnDeploy = false
  runLintRamlCop = false

  doDocker = {
    buildJavaDocker {
      publishMaster = 'no'
      healthChk = 'no'
      healthChkCmd = 'curl -sS --fail -o /dev/null  http://localhost:8081/apidocs/ || exit 1'
    }
  }
}


