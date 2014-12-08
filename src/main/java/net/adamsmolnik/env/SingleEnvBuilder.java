package net.adamsmolnik.env;

import java.util.List;
import java.util.concurrent.TimeUnit;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyResult;
import com.amazonaws.services.autoscaling.model.Tag;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.ComparisonOperator;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.amazonaws.services.cloudwatch.model.Statistic;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancing.model.ConfigureHealthCheckRequest;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerResult;
import com.amazonaws.services.elasticloadbalancing.model.DescribeInstanceHealthRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeInstanceHealthResult;
import com.amazonaws.services.elasticloadbalancing.model.HealthCheck;
import com.amazonaws.services.elasticloadbalancing.model.InstanceState;
import com.amazonaws.services.elasticloadbalancing.model.Listener;

public class SingleEnvBuilder {

    private final String baseName;

    private String elbName;

    private String asgName;

    private String elbDnsAddress;

    private AmazonCloudWatch cw = new AmazonCloudWatchClient();

    private AmazonAutoScaling asg = new AmazonAutoScalingClient();

    private AmazonElasticLoadBalancing elb = new AmazonElasticLoadBalancingClient();

    public SingleEnvBuilder(String coreName) {
        this.baseName = coreName;
    }

    public void build() {
        buildElb();
        buildAsg();
    }

    public static void main(String[] args) {
        SingleEnvBuilder seb = new SingleEnvBuilder("student001");
        seb.build();
        System.out.println("setup OK");
        seb.waitUntilReadyToWork();
        System.out.println(seb.elbDnsAddress);
    }

    private void waitUntilReadyToWork() {
        DescribeInstanceHealthResult healthResult = elb.describeInstanceHealth(new DescribeInstanceHealthRequest(elbName));
        List<InstanceState> instanceStates = healthResult.getInstanceStates();
        while (healthCondition(instanceStates)) {
            try {
                System.out.println("Waiting...");
                TimeUnit.SECONDS.sleep(15);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean healthCondition(List<InstanceState> instanceStates) {
        for (InstanceState instanceState : instanceStates) {
            if ("InService".equals(instanceState.getState())) {
                return true;
            }
        }
        return false;
    }

    private CreateLoadBalancerResult buildElb() {
        elbName = "elb-" + baseName;
        CreateLoadBalancerRequest elbRequest = new CreateLoadBalancerRequest(elbName).withAvailabilityZones("us-east-1a")
                .withListeners(new Listener("TCP", 80, 80)).withSecurityGroups("sg-7be68f1e");
        CreateLoadBalancerResult res = elb.createLoadBalancer(elbRequest);
        elbDnsAddress = res.getDNSName();
        HealthCheck hc = new HealthCheck("HTTP:80/digest-service-no-limit/hc", 30, 5, 2, 2);
        ConfigureHealthCheckRequest hcRequest = new ConfigureHealthCheckRequest(elbName, hc);
        elb.configureHealthCheck(hcRequest);
        return res;
    }

    private void buildAsg() {
        asgName = "asg-" + baseName;
        String ec2Name = "ds " + baseName + " from asg";
        CreateAutoScalingGroupRequest asgRequest = new CreateAutoScalingGroupRequest().withAutoScalingGroupName(asgName).withDesiredCapacity(1)
                .withMinSize(1).withMaxSize(3).withHealthCheckType("ELB").withLoadBalancerNames(elbName).withHealthCheckGracePeriod(300)
                .withLaunchConfigurationName("lc").withTags(new Tag().withKey("Name").withValue(ec2Name).withPropagateAtLaunch(true))
                .withVPCZoneIdentifier("subnet-a8a554df");
        asg.createAutoScalingGroup(asgRequest);
        createPolicy("out-" + asgName, asgName);
    }

    private void createPolicy(String policyName, String asgName) {
        PutScalingPolicyRequest request = new PutScalingPolicyRequest().withAutoScalingGroupName(asgName).withPolicyName(policyName)
                .withScalingAdjustment(1).withAdjustmentType("ChangeInCapacity");
        PutScalingPolicyResult result = asg.putScalingPolicy(request);
        createAlarmOut(result.getPolicyARN(), asgName, "awsec2-" + asgName + "-High-CPU-Utilization");
    }

    private void createAlarmOut(String policyArn, String asgName, String alarmName) {
        PutMetricAlarmRequest upRequest = new PutMetricAlarmRequest().withAlarmName(alarmName).withMetricName("CPUUtilization")
                .withDimensions(new Dimension().withName("AutoScalingGroupName").withValue(asgName)).withNamespace("AWS/EC2")
                .withComparisonOperator(ComparisonOperator.GreaterThanThreshold).withStatistic(Statistic.Average).withUnit(StandardUnit.Percent)
                .withThreshold(50.0).withPeriod(120).withEvaluationPeriods(1).withAlarmActions(policyArn);
        cw.putMetricAlarm(upRequest);
    }

}
