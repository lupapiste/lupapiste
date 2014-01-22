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
    self.mainConversation = ko.observable(true);

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
    
    
    var doAddComment = function(markAnswered) {
        var id = self.applicationId();
        ajax.command("add-comment", {
            id: id,
            text: self.text(),
            target: self.target(),
            to: self.to(),
            "mark-answered": markAnswered
        })
        .processing(self.processing)
        .pending(self.pending)
        .success(function() {
            self.text("").to(undefined);
            repository.load(id);
        })
        .call();
        return false;
    };
    
    self.markAnswered = function() {
        return doAddComment(true);
    };

    self.submit = function() {
        return doAddComment(false);
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
    self.isMainConversation = function(model) {
      console.log(self.mainConversation());
      return model && model.mainConversation;
    }
  }

  return {
    create: function(takeAll) { return new CommentModel(takeAll); }
  };

})();
