function TagModel(tag, saveFn) {
  "use strict";
  this.id = tag.id;
  this.label = ko.observable(tag.label);
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
    var tags = _(self.tags()).map(function(t) { return _.pick(ko.toJS(t), "id", "label"); }).uniq("label").value();
    ajax
      .command("save-organization-tags", {tags: tags})
      .success(function() {
        self.indicator({type: "saved"});
        self.refresh();
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
  }

  self.refresh = function() {
    if (saveSubscription) {
      saveSubscription.dispose();
    }
    var orgId = _(lupapisteApp.models.currentUser.orgAuthz()).keys().first();
    ajax
      .query("get-organization-tags")
      .success(function(res) {
        var tags = _.map(res.tags[orgId].tags, function(t) {
          return new TagModel(t, self.save);
        });
        self.tags(tags);
        onSave();
      })
      .call();
  };
  self.refresh();

  self.addTag = function() {
    var model = new TagModel({id: null, label: ""}, self.save);
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
