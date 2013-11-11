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
    self.markAnswered = ko.observable();

    self.to.subscribe(function(value) { if (value) self.markAnswered(false); });

    self.refresh = function(application, target) {
      self
        .applicationId(application.id)
        .target(target || {type: "application"})
        .text("")
        .markAnswered(false);
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

    self.submit = function(model) {
      var id = self.applicationId();
      ajax.command("add-comment", {
          id: id,
          text: model.text(),
          target: self.target(),
          to: self.to(),
          "mark-answered": self.markAnswered()
        })
        .processing(self.processing)
        .pending(self.pending)
        .success(function() {
          model.text("").to(undefined).markAnswered(true);
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
