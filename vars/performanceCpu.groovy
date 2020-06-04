// vars/performanceCpu.groovy
def call(m) {

    import java.lang.management.ManagementFactory
    def timestamp
    def masterPid
    def mode = ${m}

    pipeline {
    agent {
            label "master"
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
                        steps{
                            dir ("cpu/VM_Description"){
                                sh "echo 'VM.version \n ==========' > VM_description.txt"
                                sh "jcmd $masterPid VM.version >> VM_description.txt"
                                sh "echo 'VM.system_properties \n ==========' >> VM_description.txt"
                                sh "jcmd $masterPid VM.system_properties >> VM_description.txt"
                                sh "echo 'VM.flags \n ==========' >> VM_description.txt"
                                sh "jcmd $masterPid VM.flags >> VM_description.txt"
                                }
                            }
                        }
                    stage("VM Threads"){
                        environment {
                            // For option 2
                            FREQUENCY = 100
                            RUNS = 2
                        }  
                        steps{
                            echo "Running mode ${mode}"
                            dir ("cpu/VM_Threads"){
                                // Option 1 (recommended)
                                sh """
                                curl https://s3.amazonaws.com/cloudbees-jenkins-scripts/e206a5-linux/jenkinshangWithJstack.sh > jenkinshangWithJstack.sh
                                chmod +x jenkinshangWithJstack.sh
                                ./jenkinshangWithJstack.sh $masterPid
                                """
                                // Option 2 
                                // script {
                                //     for (int i = 0; i < "$RUNS".toInteger() ; i++) {
                                //         sh(script: "jcmd $masterPid Thread.print > jcmd-Thread${i}.txt", returnStdout: false)
                                //         // sh(script: "jstack -l $masterPid > jstack${i}.txt", returnStdout: false)
                                //         sleep "$FREQUENCY"
                                //     }
                                // }
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
