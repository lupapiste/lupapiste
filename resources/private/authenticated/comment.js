var comments = (function() {
  "use strict";

  function CommentModel(takeAll) {
    var self = this;

    self.target = ko.observable({type: "application"});
    self.text = ko.observable();
    self.comments = ko.observableArray();
    self.processing = ko.observable();
    self.pending = ko.observable();
    self.to = ko.observable();
    self.applicationId = null;
    
    self.refresh = function(application, target) {
      self.applicationId = application.id;
      self.target(target || {type: "application"});
      self.text("");
      var filteredComments =
        _.filter(application.comments,
            function(comment) {
              return takeAll || self.target().type === comment.target.type && self.target().id === comment.target.id;
            });
      self.comments(ko.mapping.fromJS(filteredComments));
    };

    self.isForMe = function(model) {
      return model.to && model.to.id && model.to.id() === currentUser.id();
    }

    self.disabled = ko.computed(function() {
      return self.processing() || _.isEmpty(self.text());
    });

    self.submit = function(model) {
      var id = self.applicationId;
      ajax.command("add-comment", { id: id, text: model.text(), target: self.target(), to: self.to()})
        .processing(self.processing)
        .pending(self.pending)
        .success(function() {
          model.text("");
          self.to(undefined);
          repository.load(id);
        })
        .call();
      return false;
    };

    self.isForNewAttachment = function(model) {
      return model && model.target && model.target.version && true;
    };
  }

  return {
    create: function(takeAll) { return new CommentModel(takeAll); }
  };

})();
