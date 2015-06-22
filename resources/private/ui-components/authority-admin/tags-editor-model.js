function TagModel(label) {
  this.label = ko.observable(label);
  this.edit = ko.observable(false);
};

LUPAPISTE.TagsEditorModel = function(params) {
  "use strict";

  var self = this;

  self.tags = ko.observableArray([]);

  self.indicator = ko.observable().extend({notify: "always"});

  self.save = _.debounce(function() {
    self.tags.remove(function(item) {
      return _.isEmpty(ko.unwrap(item.label));
    });
    var tags = _.map(self.tags(), function(t) { return ko.unwrap(t.label); });
    ajax
      .command("save-organization-tags", {tags: tags})
      .success(function() {
        self.indicator({type: "saved"});
      })
      .error(function() {
        self.indicator({type: "err"});
      })
      .call();
  }, 500);

  ajax
    .query("get-organization-tags")
    .success(function(res) {
      var tags = _.map(res.tags, function(t) {
        var model = new TagModel(t);
        model.label.subscribe(function(val) {
          self.save();
        })
        return model;
      });
      self.tags(tags);
      self.tags.subscribe(function() {
        self.save();
      });
    })
    .call();

  self.addTag = function() {
    var model = new TagModel();
    model.edit(true);
    self.tags.push(model);
  };

  self.removeTag = function(item) {
    item.edit(false);
    self.tags.remove(item);
  };

  self.editTag = function(item) {
    item.edit(true);
  };

  self.onKeypress = function(item, event) {
    if (event.keyCode === 13) {
      item.edit(false);
    }
    return true;
  };
};
