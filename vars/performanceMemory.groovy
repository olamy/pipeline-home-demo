// vars/performanceMemory.groovy
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
                                dir ("memory/VM_Description"){
                                    sh "echo 'VM.version \n ==========' > VM_description.txt"
                                    sh "jcmd $masterPid VM.version >> VM_description.txt"
                                    sh "echo 'VM.system_properties \n ==========' >> VM_description.txt"
                                    sh "jcmd $masterPid VM.system_properties >> VM_description.txt"
                                    sh "echo 'VM.flags \n ==========' >> VM_description.txt"
                                    sh "jcmd $masterPid VM.flags >> VM_description.txt"
                                    }
                                }
                            }
                        stage("VM Heap Dump"){
                            stages {
                                stage("jenkinsjmap.sh") {
                                    // Ref: https://support.cloudbees.com/hc/en-us/articles/115001122568
                                    when {
                                        expression { mode == "1" }
                                    }
                                    steps {
                                        sh """
                                        curl https://s3.amazonaws.com/cloudbees-jenkins-scripts/e206a5-linux/jenkinsjmap.sh > jenkinsjmap.sh
                                        chmod +x jenkinsjmap.sh
                                        ./jenkinsjmap.sh $masterPid 1
                                        """
                                    }
                                }
                                stage("jmap") {
                                    // Ref: https://support.cloudbees.com/hc/en-us/articles/222167128
                                    when {
                                        expression { mode == "2" }
                                    }
                                    script {
                                        // Heap Dump
                                        sh "jmap -dump:format=b,file=heap_dump.hprof $masterPid" 
                                        // Class Histogram (Classes taking the most memory are listed at the top, and classes are listed in a descending order)
                                        sh "jcmd $masterPid GC.class_histogram > class_histogram.txt"
                                    }
                                }
                            }
                        }
                    }
                }
            }
            post {
                success {
                    zip zipFile: "Memory_Data-${timestamp}.zip", archive: true, dir: "memory"
                }
            }
        }
}