LUPAPISTE.ConversationModel = function(params) {
  "use strict";
  var self = this;

  self.application = params.application;
  self.authorization = params.authorization;
  self.currentPage = params.currentPage;

  self.authorities = params.authorities;

  self.text = ko.observable();
  self.textSelected = ko.observable();
  self.sendingDisabled = ko.observable();
  self.processing = ko.observable();
  self.pending = ko.observable();
  self.to = ko.observable();
  self.showAttachmentComments = ko.observable(true);
  self.showPreparationComments = ko.observable(false);

  self.comments = params.comments;

  self.mainConversation = ko.observable(true);
  self.highlightConversation = ko.observable(false);

  var previousHash = lupapisteApp.models.rootVMO.previousHash;

  self.infoRequest = ko.pureComputed(function() {
    return util.getIn(self, ["application", "infoRequest"]);
  });

  self.applicationId = ko.computed(function() {
    return util.getIn(self, ["application", "id"]);
  });

  self.canTargetToAuthority = ko.pureComputed(function() {
    return self.authorization.ok("can-target-comment-to-authority") ||
           self.authorization.ok("can-mark-answered");
  });

  self.showStateOpenApplication = ko.pureComputed(function() {
    return lupapisteApp.models.currentUser.isApplicant();
  });

  self.stateOpenApplication = function() {
    console.log("open");
  };

  self.submit = function() {
    console.log("submit");
  };

  self.markAnswered = function () {
    console.log("answered");
  };

  // function refreshConversations(page) {
  //   if (page) {
  //     var type = pageutil.getPage();
  //     self.mainConversation(true);
  //     switch(type) {
  //       case "attachment":
  //         self.mainConversation(false);
  //         self.comment.refresh(self.application, false, {type: type, id: pageutil.lastSubPage()});
  //         break;
  //       case "statement":
  //         self.mainConversation(false);
  //         self.comment.refresh(self.application, false, {type: type, id: pageutil.lastSubPage()});
  //         break;
  //       case "verdict":
  //         self.mainConversation(false);
  //         self.comment.refresh(self.application, false, {type: type, id: pageutil.lastSubPage()}, ["authority"]);
  //         break;
  //       default:
  //         self.comment.refresh(self.application, true);
  //         break;
  //     }
  //   }
  // }

  var previousPage = self.currentPage();

  function highlightConversation() {
    hub.send("show-conversation");
    self.comment.isSelected(true);
    self.highlightConversation(true);
    setTimeout(function() {
      self.highlightConversation(false);
    }, 2000);
  }

  ko.computed(function() {
    // refreshConversations(self.currentPage());
    // if (self.currentPage() !== previousPage && self.comment.text()) {
    //   hub.send("show-dialog", {ltitle: "application.conversation.unsentMessage.header",
    //                            size: "medium",
    //                            component: "yes-no-dialog",
    //                            componentParams: {ltext: "application.conversation.unsentMessage",
    //                                              yesFn: function() {
    //                                                if (previousHash()) {
    //                                                  location.hash = previousHash();
    //                                                }
    //                                                highlightConversation();
    //                                              },
    //                                              noFn: function() {
    //                                                self.comment.text(undefined);
    //                                                previousPage = self.currentPage();
    //                                              },
    //                                              lyesTitle: "application.conversation.sendMessage",
    //                                              lnoTitle: "application.conversation.clearMessage"}});
    // } else {
    //   previousPage = self.currentPage();
    // }
  }).extend({ rateLimit: 100 });


  self.isAuthorityComment = function(comment) {
    return util.getIn(comment, ["user", "role"]) === "authority";
  };

  self.isForAttachment = function(comment) {
    return util.getIn(comment, ["target", "type"]) === "attachment";
  };

  function isPreparationComment(comment) {
    // verdict's comments
    return util.getIn(comment, ["roles", 0]) === "authority";
  }

  self.isVisible = function(comment) {
    return !self.takeAll ||
             ((self.showAttachmentComments()  || !self.isForAttachment(comment)) &&
              (!isPreparationComment(comment) || self.showPreparationComments()));
  };

  self.isForMe = function(comment) {
    return util.getIn(comment, ["to", "id"]) === lupapisteApp.models.currentUser.id();
  };
};
