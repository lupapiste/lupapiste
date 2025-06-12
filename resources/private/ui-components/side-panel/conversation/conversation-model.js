LUPAPISTE.ConversationModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.addComment = function (params) {
    self.pending(true);
    self.sendEvent("SidePanelService", "AddComment", params);
  };

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
  self.oldestFirst = params.oldestFirst;

  self.highlightConversation = ko.observable(false);

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
    self.addComment({
      openApplication: true,
      text: self.text(),
      to: self.to()
    });
  };

  self.submit = function() {
    self.addComment({
      text: self.text(),
      to: self.to()
    });
  };

  self.markAnswered = function () {
    self.addComment({
      markAnswered: true,
      text: self.text(),
      to: self.to()
    });
  };

  self.isAuthorityComment = function(comment) {
    return util.getIn(comment, ["type"]) === "authority";
  };

  self.isSystemComment = function(comment) {
    return util.getIn(comment, ["type"]) === "system";
  };

  self.isForAttachment = function(comment) {
    return util.getIn(comment, ["target", "type"]) === "attachment";
  };

  self.getAttachmentTypeLocKey = function(comment) {
    return ["attachmentType",
            util.getIn(comment, ["target", "attachmentType", "type-group"]),
            util.getIn(comment, ["target", "attachmentType", "type-id"])]
      .join(".");
  };

  self.getCommentText = function(comment) {
    // Only override system comment's text so we don't hide user messages
    var attachmentText = self.isSystemComment(comment) && util.getIn(comment, ["target", "attachmentContents"]);
    if (attachmentText) {
      // If the attachment was fetched from the backend system, indicate when
      // it happened
      var fetched = util.getIn(comment, ["target", "attachmentFetched"]);
      if (fetched) {
        attachmentText += "\n" + loc("attachment.fetched") + ": " + util.finnishDateAndTime(fetched);
      }
    }
    return attachmentText || comment.text;
  };

  function isPreparationComment(comment) {
    // verdict's comments
    return util.getIn(comment, ["roles", 0]) === "authority";
  }

  function commentVisibilityCheck(comment) {
    if (self.isForAttachment(comment)) {
      return self.showAttachmentComments();
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

  self.addEventListener("SidePanelService", "AddCommentProcessed", function(event) {
    self.pending(false);
    if (event.status === "success") {
      self.text("");
      self.to("");
    }
  });

  self.addEventListener("SidePanelService", "AddCommentFailed", function(event) {
    util.showErrorDialog(event);
    self.pending(false);
  });

  // Fallback for system type is the user role. The fallback does not
  // handle outside authorities correctly. The more robust approach
  // would be to resolve the role in the backend.
  self.commentRole = function( data ) {
    return "applicationRole." + _.get(data, "user.application-role", "unknown");
  };

  self.addEventListener( "contextService", "enter", function() {
    self.to( null );
    self.text( "" );
  });
};
