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

LUPAPISTE.TagsEditorModel = function() {
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
      .success(function(res) {
        util.showSavedIndicator(res);
        self.refresh();
      })
      .error(util.showSavedIndicator)
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
        var tags = [];
        if (!_.isEmpty(res.tags)) {
          tags = _.map(res.tags[orgId].tags, function(t) {
            return new TagModel(t, self.save);
          });
        }
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
    var removeFn = function() {
      item.edit(false);
      item.dispose();
      self.tags.remove(item);
    };

    if (item.id) {
      ajax
        .query("remove-tag-ok", {tagId: item.id})
        .onError("warning.tags.removing-from-applications", function(data) {
          var applications = _.map(data.applications, "id");
          var dialogTextPrefix = loc("tags.removing-from-applications.prefix", item.label());

          var dialogBody = _.reduce(applications, function(resultStr, idStr) {
            return resultStr + "<div><i>" + idStr + "</i></div>";
          }, "<div class='spacerM'>");
          dialogBody = dialogBody + "</div>";

          var dialogTextSuffix = loc("tags.removing-from-applications.suffix");

          hub.send("show-dialog",
                   {ltitle: "tags.deleting",
                    size: "medium",
                    component: "yes-no-dialog",
                    componentParams: {text: dialogTextPrefix + dialogBody + dialogTextSuffix,
                                      yesFn: removeFn}});
        })
        .success(function() {
          hub.send("show-dialog",
                   {ltitle: "tags.deleting",
                    size: "medium",
                    component: "yes-no-dialog",
                    componentParams: {text: loc("tags.deleting.confirmation", item.label()), yesFn: removeFn}});
        })
        .call();
      } else { // no ID
        item.edit(false);
        item.dispose();
        saveSubscription.dispose();
        self.tags.remove(item);
        onSave();
      }

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
