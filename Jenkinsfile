#!/usr/bin/env groovy

def defaultParams = [
    string(name: 'SKIP_TESTS', defaultValue: 'false', description: 'Skip tests (true or false)')
]

def config = jobConfig {
    nodeLabel = 'docker-oraclejdk8'
    properties = [parameters(defaultParams)]
    testResultSpecs = ['junit': '**/build/test-results/**/TEST-*.xml']
    timeoutHours = 4
}

def job = {
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
                    }
                }
            }
        }
    }
}

runJob config, job
