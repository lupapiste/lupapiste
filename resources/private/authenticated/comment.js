var comments = (function() {
  "use strict";

  function CommentModel() {
    var self = this;

    self.target = ko.observable({type: "application"});
    self.text = ko.observable();
    self.comments = ko.observableArray();

    //TODO: hide all other mutators
    self.refresh = function(application, target) {
      self.setApplicationId(application.id);
      self.setTarget(target || {type: "application"});
      self.setComments(application.comments);
    };

    self.setComments = function(comments, takeAll) {
      var filteredComments =
        _.filter(comments,
            function(comment) {
              return takeAll || self.target().type === comment.target.type && self.target().id === comment.target.id;
            });
      self.comments(ko.mapping.fromJS(filteredComments));
    };

    self.setTarget = function(target) {
      self.target(target);
    };

    self.setApplicationId = function(applicationId) {
      self.applicationId = applicationId;
    };

    self.disabled = ko.computed(function() {
      return _.isEmpty(self.text());
    });

    self.submit = function(model) {
      var id = self.applicationId;
      ajax.command("add-comment", { id: id, text: model.text(), target: self.target()})
        .success(function() {
          model.text("");
          repository.load(id);
        })
        .call();
      return false;
    };

    self.isForNewAttachment = function(model) {
      return model && model.target && model.target.version ? true : false;
    };

    self.category = function(model) {
      return model.target && model.target.version ? "category-new-attachment-version" : "category-default";
    };
  }

  return {
    create: function() { return new CommentModel(); }
  };

})();
