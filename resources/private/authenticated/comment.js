var comments = (function() {
  "use strict";

  function CommentModel(takeAll) {
    var self = this;

    self.target = ko.observable({type: "application"});
    self.text = ko.observable();
    self.comments = ko.observableArray();
    self.processing = ko.observable();
    self.pending = ko.observable();

    self.refresh = function(application, target) {
      self.setApplicationId(application.id);
      self.target(target || {type: "application"});
      var filteredComments =
        _.filter(application.comments,
            function(comment) {
              return takeAll || self.target().type === comment.target.type && self.target().id === comment.target.id;
            });
      self.comments(ko.mapping.fromJS(filteredComments));
    };

    self.setApplicationId = function(applicationId) {
      self.applicationId = applicationId;
    };

    self.disabled = ko.computed(function() {
      return self.processing() || _.isEmpty(self.text());
    });

    self.submit = function(model) {
      var id = self.applicationId;
      ajax.command("add-comment", { id: id, text: model.text(), target: self.target()})
        .processing(self.processing)
        .pending(self.pending)
        .success(function() {
          model.text("");
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
