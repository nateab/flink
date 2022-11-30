#!/usr/bin/env groovy

def defaultParams = [
    string(name: 'SKIP_TESTS', defaultValue: 'false', description: 'Skip tests (true or false)')
]

def config = jobConfig {
    nodeLabel = 'docker-oraclejdk8'
    properties = [parameters(defaultParams)]
    testResultSpecs = ['junit': '**/build/test-results/**/TEST-*.xml']
    timeoutHours = 4
    slackChannel = '#ksqldb-quality-oncall'
}

def job = {
    if (config.isPrJob) {
        config.slackChannel = ''
    }

    stage('Build') {
        def skipTestsArgs = ''
        if (params.SKIP_TESTS.trim() == 'true') {
            skipTestsArgs = '-DskipTests'
        }

        def mavenSettingsFile = "${env.WORKSPACE_TMP}/maven-global-settings.xml"
        withVaultEnv([["artifactory/tools_jenkins", "user", "TOOLS_ARTIFACTORY_USER"],
                      ["artifactory/tools_jenkins", "password", "TOOLS_ARTIFACTORY_PASSWORD"]]) {
            withDockerServer([uri: dockerHost()]) {
                withMavenSettings("maven/jenkins_maven_global_settings", "settings", "MAVEN_GLOBAL_SETTINGS", mavenSettingsFile) {
                    withMaven(globalMavenSettingsFilePath: mavenSettingsFile) {
                        sh """
                            ./mvnw --batch-mode --fail-at-end clean package ${skipTestsArgs}
                            ./mvnw --batch-mode deploy ${skipTestsArgs}
                           """
                        sh '''
                            VERSION=$(./mvnw org.apache.maven.plugins:maven-help-plugin:3.1.0:evaluate -Dexpression=project.version | egrep '^[0-9]')
                            ln -s build-target flink
                            tar -chf confluent-flink.tar.gz flink
                            ./mvnw deploy:deploy-file -DgroupId=io.confluent.flink \
                                  -DartifactId=flink \
                                  -Dversion=${VERSION} \
                                  -Dpackaging=tar.gz \
                                  -Dfile=confluent-flink.tar.gz \
                                  -DrepositoryId=confluent-artifactory-internal \
                                  -Durl=https://confluent.jfrog.io/confluent/maven-releases
                           '''
                    }
                }
            }
        }
    }
}

runJob config, job
