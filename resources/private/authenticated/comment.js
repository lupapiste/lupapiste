var comments = (function() {
  "use strict";

  function CommentModel() {
    var self = this;

    self.applicationId = null;
    self.takeAll = false;
    self.newCommentRoles = undefined;

    self.target = ko.observable({type: "application"});
    self.text = ko.observable();
    self.comments = ko.observableArray();
    self.processing = ko.observable();
    self.pending = ko.observable();
    self.to = ko.observable();
    self.showAttachmentComments = ko.observable(false);
    self.showPreparationComments = ko.observable(false);
    self.isSelected = ko.observable();

    self.refresh = function(application, takeAll, target, newCommentRoles) {
      var oldValue = self.text();
      self.applicationId = application.id;
      self.target(target || {type: "application"}).text("");
      self.takeAll = takeAll;
      self.newCommentRoles = newCommentRoles;
      var filteredComments =
        _.filter(application.comments,
          function(comment) {
            return self.takeAll || self.target().type === comment.target.type && self.target().id === comment.target.id;
          });
      self.comments(ko.mapping.fromJS(filteredComments));
      self.text(oldValue);
    };

    self.isForMe = function(model) {
      return model.to && model.to.id && model.to.id() === lupapisteApp.models.currentUser.id();
    };

    self.disabled = ko.computed(function() {
      return self.processing() || _.isEmpty(_.trim(self.text()));
    });

    var doAddComment = function(markAnswered, openApplication) {
      ajax.command("add-comment", {
          id: self.applicationId,
          text: self.text() ? _.trim(self.text()) : "",
          target: self.target(),
          to: self.to(),
          roles: self.newCommentRoles || ["applicant","authority"],
          "mark-answered": markAnswered,
          openApplication: openApplication
      })
      .processing(self.processing)
      .pending(self.pending)
      .success(function() {
          self.text("").to(undefined);
          if (markAnswered) {
              LUPAPISTE.ModalDialog.showDynamicOk(loc("comment-request-mark-answered-label"), loc("comment-request-mark-answered.ok"));
          }
          repository.load(self.applicationId);
      })
      .call();
      return false;
    };

    self.markAnswered = function() {
      hub.send("track-click", {category:"Conversation", event:"markAnswered"});
      return doAddComment(true, false);
    };

    self.submit = function() {
      hub.send("track-click", {category:"Conversation", event:"submit"});
      return doAddComment(false, false);
    };

    self.stateOpenApplication = function() {
      hub.send("track-click", {category:"Conversation", event:"stateOpenApplication"});
      return doAddComment(false, true);
    };

    self.isAuthorityComment = function(model) {
      return util.getIn(model, ["user", "role"]) === "authority";
    };

    self.isForAttachment = function(model) {
      return util.getIn(model, ["target", "type"]) === "attachment";
    };

    function isPreparationComment(model) {
      return model && model.roles().length === 1 && model.roles()[0] === "authority";
    };

    self.isVisible = function(model) {
      return !self.takeAll ||
               (self.showAttachmentComments() &&
                (!isPreparationComment(model)    || self.showPreparationComments()));
    };
  }

  return {
    create: function() {
      return new CommentModel(); }
  };

})();
