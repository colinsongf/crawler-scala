package org.blikk.test

import akka.routing.{Broadcast, AddRoutee, ActorRefRoutee, Routee, ConsistentHashingGroup}
import akka.routing.ConsistentHashingRouter.ConsistentHashableEnvelope
import akka.actor._
import akka.testkit._
import org.blikk.crawler._

class CrawlServiceSpec extends AkkaSingleNodeSpec("CrawlServiceSpec") {

  val jobConf = JobConfiguration.empty("testJob").copy(
    processors = List(new TestResponseProcessor(self)))

  describe("CrawlService") {
    
    describe("Registering an existing job") {
        
        it("should work") {
          val service = TestActorRef(TestCrawlService.props, "crawlService1")
          service.receive(RegisterJob(jobConf, true))
          service.receive(GetJob("testJob"), self)
          expectMsg(jobConf)
          service.stop()
        }
    }

    describe("Routing a request") {
      
      it("should work when the worker does not exist yet") {
        val service = TestActorRef(TestCrawlService.props, "crawlService2")
        service.receive(RegisterJob(jobConf, true))
        service.receive(new FetchRequest(WrappedHttpRequest.getUrl("http://localhost:9090"), "testJob"))
        expectMsg("http://localhost:9090")
        service.stop()
      }

      it("should work with multiple requests to the same host") {
        val service = TestActorRef(TestCrawlService.props, "crawlService3")
        service.receive(RegisterJob(jobConf, true))
        service.receive(new FetchRequest(WrappedHttpRequest.getUrl("http://localhost:9090/1"), "testJob"))
        service.receive(new FetchRequest(WrappedHttpRequest.getUrl("http://localhost:9090/2"), "testJob"))
        service.receive(new FetchRequest(WrappedHttpRequest.getUrl("http://localhost:9090/3"), "testJob"))
        receiveN(3).toSet == Set("http://localhost:9090/1", "http://localhost:9090/2", "http://localhost:9090/3")
        service.stop()
      }
    }

    describe("Running a new job with multiple requests") {

      it("should work") {
        val service = TestActorRef(TestCrawlService.props, "crawlService4")
        val confWithSeeds= jobConf.copy(seeds = List(WrappedHttpRequest.getUrl("http://localhost:9090/1"), 
          WrappedHttpRequest.getUrl("http://localhost:9090/2")))
        service.receive(RunJob(confWithSeeds, true))
        receiveN(2).toSet == Set("http://localhost:9090/1", "http://localhost:9090/2")
        service.receive(new FetchRequest(WrappedHttpRequest.getUrl("http://localhost:9090/3"), "testJob"))
        service.receive(new FetchRequest(WrappedHttpRequest.getUrl("http://localhost:9090/4"), "testJob"))
        receiveN(2).toSet == Set("http://localhost:9090/3", "http://localhost:9090/4")
        expectNoMsg()
        service.stop()
      }
    }

    describe("Terminating a job") {
      it("should work") {
        val service = TestActorRef(TestCrawlService.props, "crawlService8")
        service.receive(RegisterJob(jobConf, true))
        service.receive(new FetchRequest(WrappedHttpRequest.getUrl("http://localhost:9090/1"), "testJob"))
        service.receive(new FetchRequest(WrappedHttpRequest.getUrl("http://localhost:9090/2"), "testJob"))
        receiveN(2).toSet == Set("http://localhost:9090/1", "http://localhost:9090/2")
        service.receive(Broadcast(StopJob("testJob")))
        service.receive(new FetchRequest(WrappedHttpRequest.getUrl("http://localhost:9090/3"), "testJob"))
        expectNoMsg()
        service.stop()
      }
    }

  }

}