// vars/performanceCpu.groovy

import java.lang.management.ManagementFactory

def call(mode) {

    def timestamp
    def masterPid
    def data_script

    pipeline {
        agent {
            label "master"
        }
        options { // Increase Rotation with integration with External Storage
            buildDiscarder(logRotator(numToKeepStr: "1", artifactNumToKeepStr: "1"))
        }
        environment {
            MODE = "${mode}"
        }
        stages {
            stage("Prepare") {
                steps {
                    deleteDir()
                    script {
                        timestamp = sh(script: "date +%d-%b-%Y_%H-%M-%S", returnStdout: true).trim()
                        def master_name_array = ManagementFactory.getRuntimeMXBean().getName().split("@");
                        masterPid = master_name_array[0]
                    }
                }
            }
            stage("Collect data") {
                stages {
                    stage("VM Description"){
                        environment {
                            OUTPUT = "VM_description.txt"
                        }
                        steps{
                            dir ("cpu"){
                                sh """
                                    echo '==========\nVM.version\n==========\n\n' >> $OUTPUT
                                    jcmd $masterPid VM.version >> $OUTPUT
                                    echo '==========\nVM.system_properties\n==========\n\n' >> $OUTPUT
                                    jcmd $masterPid VM.system_properties >> $OUTPUT
                                    echo '==========\nVM.flags\n==========\n\n' >> $OUTPUT
                                    jcmd $masterPid VM.flags >> $OUTPUT
                                """
                                }
                            }
                        }
                    stage("VM Threads"){
                        stages { 
                            stage("via script") {
                                // Ref: https://support.cloudbees.com/hc/en-us/articles/229370407
                                when {
                                    environment name: 'MODE', value: '1'
                                }
                                steps {
                                    dir ("cpu"){
                                        sh """
                                        curl https://s3.amazonaws.com/cloudbees-jenkins-scripts/e206a5-linux/jenkinshangWithJstack.sh > jenkinshangWithJstack.sh
                                        chmod +x jenkinshangWithJstack.sh
                                        ./jenkinshangWithJstack.sh $masterPid
                                        """
                                    }
                                }
                            }
                            stage("via jdk") {
                                // Ref: https://support.cloudbees.com/hc/en-us/articles/205199280
                                when {
                                    environment name: 'MODE', value: '2'
                                }
                                environment {
                                    FREQUENCY = 30
                                    RUNS = 8
                                }
                                steps {
                                    dir ("cpu/VM_Threads"){
                                        script {
                                            for (int i = 0; i < "$RUNS".toInteger() ; i++) {
                                                sh(script: "jcmd $masterPid Thread.print > jcmd-Thread${i}.txt", returnStdout: false)
                                                sleep "$FREQUENCY"
                                            }
                                        }
                                    }
                                }
                            }
                            stage("via offline") {
                                // Ref: https://support.cloudbees.com/hc/en-us/articles/205199280
                                when {
                                    environment name: 'MODE', value: '3'
                                }
                                steps {
                                    dir ("cpu"){
                                        script {
                                            data_script = libraryResource 'scripts/jenkinshangWithJstack.sh'
                                        }
                                        writeFile file: "data_script.sh", text: data_script
                                        sh """
                                        chmod +x data_script.sh
                                        bash data_script.sh $masterPid
                                        """
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        post {
            success {
                zip zipFile: "CPU_Data-${timestamp}.zip", archive: true, dir: "cpu"
            }
        }
    }
}
