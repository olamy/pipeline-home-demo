// vars/performanceCpu.groovy

import java.lang.management.ManagementFactory

def call(mode) {

    def timestamp
    def masterPid

    pipeline {
        agent {
            label "master"
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
                            stage("jenkinshangWithJstack.sh") {
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
                            stage("jcmd Thread.print") {
                                // Ref: https://support.cloudbees.com/hc/en-us/articles/205199280
                                when {
                                    environment name: 'MODE', value: '2'
                                }
                                environment {
                                    FREQUENCY = 100
                                    RUNS = 2
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
