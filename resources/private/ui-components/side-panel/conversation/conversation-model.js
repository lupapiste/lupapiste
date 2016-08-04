LUPAPISTE.ConversationModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.application = params.application;
  self.authorization = params.authorization;
  self.currentPage = params.currentPage;

  self.authorities = params.authorities;

  self.text = ko.observable();
  self.textSelected = ko.observable();
  self.processing = ko.observable();
  self.pending = ko.observable(false);
  self.to = ko.observable();
  self.showAttachmentComments = ko.observable(true);
  self.showPreparationComments = ko.observable(false);

  self.comments = params.comments;
  self.showAllComments = params.showAllComments;
  self.mainConversation = params.mainConversation;

  self.highlightConversation = ko.observable(false);

  var previousHash = lupapisteApp.models.rootVMO.previousHash;

  self.submitDisabled = ko.pureComputed(function() {
    return self.pending() || _.isEmpty(self.text());
  });

  self.infoRequest = ko.pureComputed(function() {
    return util.getIn(self, ["application", "infoRequest"]);
  });

  self.applicationId = ko.pureComputed(function() {
    return util.getIn(self, ["application", "id"]);
  });

  self.canTargetToAuthority = ko.pureComputed(function() {
    return self.authorization.ok("can-target-comment-to-authority") ||
           self.authorization.ok("can-mark-answered");
  });

  self.showStateOpenApplication = ko.pureComputed(function() {
    return lupapisteApp.models.currentUser.isApplicant() &&
      util.getIn(self, ["application", "state"]) === "draft" &&
      !self.infoRequest();
  });

  self.stateOpenApplication = function() {
    hub.send("track-click", {category:"Conversation", event:"stateOpenApplication"});
    self.sendEvent("SidePanelService", "AddComment", {openApplication: true,
                                                      text: self.text(),
                                                      to: self.to()});
  };

  self.submit = function() {
    hub.send("track-click", {category:"Conversation", event:"submit"});
    self.sendEvent("SidePanelService", "AddComment", {text: self.text(),
                                                      to: self.to()});
  };

  self.markAnswered = function () {
    hub.send("track-click", {category:"Conversation", event:"markAnswered"});
    self.sendEvent("SidePanelService", "AddComment", {markAnswered: true,
                                                      text: self.text(),
                                                      to: self.to()});
  };

  var previousPage = self.currentPage();

  function highlightConversation() {
    self.sendEvent("side-panel", "show-conversation");
    self.textSelected(true);
    self.highlightConversation(true);
    setTimeout(function() {
      self.highlightConversation(false);
    }, 2000);
  }

  self.disposedComputed(function() {
    if (self.currentPage() !== previousPage && self.text()) {
      hub.send("show-dialog", {ltitle: "application.conversation.unsentMessage.header",
                               size: "medium",
                               component: "yes-no-dialog",
                               componentParams: {ltext: "application.conversation.unsentMessage",
                                                 yesFn: function() {
                                                   if (previousHash()) {
                                                     location.hash = previousHash();
                                                   }
                                                   highlightConversation();
                                                 },
                                                 noFn: function() {
                                                   self.text(undefined);
                                                   previousPage = self.currentPage();
                                                 },
                                                 lyesTitle: "application.conversation.sendMessage",
                                                 lnoTitle: "application.conversation.clearMessage"}});
    } else {
      previousPage = self.currentPage();
    }
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

  self.isCalendarComment = function(comment) {
    var res = _.startsWith(util.getIn(comment, ["target", "type"]), "reservation");
    return res;
  }

  self.showCalendarComments = function() { return lupapisteApp.models.applicationAuthModel.ok("calendars-enabled"); };

  function commentVisibilityCheck(comment) {
    if (self.isForAttachment(comment)) {
      return self.showAttachmentComments();
    } else if (self.isCalendarComment(comment)) {
      return self.showCalendarComments();
    } else if (isPreparationComment(comment)) {
      return self.showPreparationComments();
    } else {
      return true;
    }
  }

  self.isVisible = function(comment) {
    return !self.showAllComments() || commentVisibilityCheck(comment);
  };

  self.isForMe = function(comment) {
    return util.getIn(comment, ["to", "id"]) === lupapisteApp.models.currentUser.id();
  };

  self.addEventListener("SidePanelService", "AddCommentProcessing", function(event) {
    self.pending(event.state === "pending");
  });

  self.addEventListener("SidePanelService", "AddCommentProcessed", function(event) {
    if (event.status === "success") {
      self.text("");
      self.to("");
    }
  });
};
