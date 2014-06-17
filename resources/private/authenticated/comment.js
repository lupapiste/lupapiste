var comments = (function() {
  "use strict";

  function CommentModel(takeAll) {
    var self = this;

    self.applicationId = ko.observable();
    self.target = ko.observable({type: "application"});
    self.text = ko.observable();
    self.comments = ko.observableArray();
    self.processing = ko.observable();
    self.pending = ko.observable();
    self.to = ko.observable();
    self.hideAttachmentComments = ko.observable(false);

    self.refresh = function(application, target) {
      self
        .applicationId(application.id)
        .target(target || {type: "application"})
        .text("");
      var filteredComments =
        _.filter(application.comments,
            function(comment) {
              return takeAll || self.target().type === comment.target.type && self.target().id === comment.target.id;
            });
      self.comments(ko.mapping.fromJS(filteredComments));
    };

    self.isForMe = function(model) {
      return model.to && model.to.id && model.to.id() === currentUser.id();
    };

    self.disabled = ko.computed(function() {
      return self.processing() || _.isEmpty(self.text());
    });


    var doAddComment = function(markAnswered, openApplication) {
        var id = self.applicationId();
        ajax.command("add-comment", {
            id: id,
            text: self.text(),
            target: self.target(),
            to: self.to(),
            roles: [], // TODO
            "mark-answered": markAnswered,
            openApplication: openApplication
        })
        .processing(self.processing)
        .pending(self.pending)
        .success(function() {
            self.text("").to(undefined);
            if (markAnswered) {
                LUPAPISTE.ModalDialog.showDynamicOk(loc('comment-request-mark-answered-label'), loc('comment-request-mark-answered.ok'));
            }
            repository.load(id);
        })
        .call();
        return false;
    };

    self.markAnswered = function() {
      return doAddComment(true, false);
    };

    self.submit = function() {
      return doAddComment(false, false);
    };

    self.stateOpenApplication = function() {
      return doAddComment(false, true);
    };

    self.isForNewAttachment = function(model) {
      return model && model.target && model.target.version && true;
    };
    self.isAuthorityComment = function(model) {
      return model.user && model.user.role && model.user.role() === "authority";
    };
    self.isForAttachment = function(model) {
      return model && model.target && model.target.type() === "attachment";
    };
  }

  return {
    create: function(takeAll) { return new CommentModel(takeAll); }
  };

})();
