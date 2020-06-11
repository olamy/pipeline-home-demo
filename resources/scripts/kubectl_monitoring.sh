
namespace_name="cje-support-general"
pod_name="cjoc-0"
watch -n1 "echo 'NODES\n'; kubectl top nodes; echo '\n\n' ; \
           echo 'PODS\n'; kubectl -n ${namespace_name} top pods; echo '\n\n'; \
           echo 'CONTAINERS\n'; kubectl -n ${namespace_name} top pod ${pod_name} --containers"