def pcImg

node {
  git '/tmp/repo'

  def maven = docker.image('maven:3.3.3-jdk-8'); // https://registry.hub.docker.com/_/maven/

  stage 'Mirror'
  // First make sure the slave has this image.
  // (If you could set your registry below to mirror Docker Hub,
  // this would be unnecessary as maven.inside would pull the image.)
  maven.pull()

  docker.withRegistry('https://docker.example.com/', 'docker-registry-login') {

    stage 'Build'
    maven.inside('-v /m2repo:/m2repo') {
      // Build and do Sonar analysis with Maven settings.xml file that specs the local Maven repo.
      sh 'mvn -f app -B -s settings.xml -DskipTests clean package'

      // The app .war and Dockerfile are now available in the workspace. See below.
    }
    
    stage 'Bake Docker image'
    // Use the spring-petclinic Dockerfile (see above 'maven.inside()' block)
    // to build a container that can run the app.
    pcImg = docker.build("examplecorp/spring-petclinic:${env.BUILD_TAG}", 'app')

    // Let us tag and push the newly built image. Will tag using the image name provided
    // in the 'docker.build' call above (which included the build number on the tag).
    pcImg.push();

    stage 'Test Image'
    // Spin up a Maven + Xvnc test container, linking it to the petclinic app container
    // allowing the Maven tests to send HTTP requests between the containers.
    def testImg = docker.build('examplecorp/spring-petclinic-tests:snapshot', 'test')
    // Run the petclinic app in its own Docker container.
    pcImg.withRun {petclinic ->
      testImg.inside("-v /m2repo:/m2repo --link=${petclinic.id}:petclinic") {
        // https://github.com/jenkinsci/workflow-plugin/blob/master/basic-steps/CORE-STEPS.md#build-wrappers
        wrap([$class: 'Xvnc', takeScreenshot: true, useXauthority: true]) {
          sh 'mvn -f test -B -s settings.xml clean test'
        }
      }
    }
  }
}

input "How do you like ${env.BUILD_URL}artifact/screenshot.jpg ?"

node {

  docker.withRegistry('https://docker.example.com/', 'docker-registry-login') {
    stage name: 'Promote Image', concurrency: 1
    // All the tests passed. We can now retag and push the 'latest' image.
    pcImg.push('latest')
  }
}

