function TagModel(label, saveFn) {
  "use strict";
  this.label = ko.observable(label);
  this.edit = ko.observable(false);

  this.saveSubscription = this.label.subscribe(function() {
    saveFn();
  });

  this.dispose = function() {
    this.saveSubscription.dispose();
  };
}

LUPAPISTE.TagsEditorModel = function(params) {
  "use strict";

  var self = this;

  self.tags = ko.observableArray([]);

  self.indicator = ko.observable().extend({notify: "always"});

  self.save = _.debounce(function() {
    self.tags.remove(function(item) {
      return _.isEmpty(ko.unwrap(item.label));
    });
    var tags = _(self.tags()).map(function(t) { return ko.unwrap(t.label); }).uniq().value();
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

  var saveSubscription;

  function onSave() {
    saveSubscription = self.tags.subscribe(function() {
      self.save();
    });
  };

  ajax
    .query("get-organization-tags")
    .success(function(res) {
      var tags = _.map(res.tags, function(t) {
        var model = new TagModel(t, self.save);
        return model;
      });
      self.tags(tags);
      onSave();
    })
    .call();

  self.addTag = function() {
    var model = new TagModel(undefined, self.save);
    model.edit(true);
    saveSubscription.dispose();
    self.tags.push(model);
    onSave();
  };

  self.removeTag = function(item) {
    item.edit(false);
    item.dispose();
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
