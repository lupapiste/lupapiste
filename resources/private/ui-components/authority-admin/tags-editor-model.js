function TagModel(label) {
  this.label = ko.observable(label);
  this.edit = ko.observable(false);
};

LUPAPISTE.TagsEditorModel = function(params) {
  "use strict";

  var self = this;

  self.tags = ko.observableArray([]);

  self.indicator = ko.observable();

  self.save = _.debounce(function() {
    console.log("save", _.map(self.tags(), function(t) { return ko.unwrap(t.label); }));
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

  self.removeTag = function(item) {
    item.edit(false);
    self.tags.remove(item);
  }

  self.editTag = function(item) {
    item.edit(true);
  }

  self.onKeypress = function(item, event) {
    if (event.keyCode === 13) {
      item.edit(false);
    }
    return true;
  }
};
