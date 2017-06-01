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

  self.refresh = function() {
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
      })
      .call();
  };
  self.refresh();

  self.addTag = function() {
    var model = new TagModel({id: null, label: ""}, self.save);
    model.edit(true);
    self.tags.push(model);
  };

  function remove(tag) {
    tag.edit(false);
    tag.dispose();
    self.tags.remove(tag);
    if (tag.id) {
      self.save();
    }
  }

  self.confirmRemoveTagDialog = function(tag, message) {
    hub.send("show-dialog",
             {ltitle: "tags.deleting",
              size: "medium",
              component: "yes-no-dialog",
              componentParams: {text: message, yesFn: _.partial(remove, tag)}});
  };

  self.removeTagFromApplicationsWarning = function(tag, data) {
    var applications = _.map(data.applications, "id");
    var dialogTextPrefix = loc("tags.removing-from-applications.prefix", tag.label());

    var dialogBody = _.reduce(applications, function(resultStr, idStr) {
      return resultStr + "<div><i>" + idStr + "</i></div>";
    }, "<div class='spacerM'>");
    dialogBody = dialogBody + "</div>";

    var dialogTextSuffix = loc("tags.removing-from-applications.suffix");

    self.confirmRemoveTagDialog(tag, dialogTextPrefix + dialogBody + dialogTextSuffix);
  };

  self.removeTag = function(tag) {
    if (tag.id) {
      ajax
        .query("remove-tag-ok", {tagId: tag.id})
        .onError("warning.tags.removing-from-applications", _.partial(self.removeTagFromApplicationsWarning, tag))
        .success(_.partial(self.confirmRemoveTagDialog, tag, loc("tags.deleting.confirmation", tag.label())))
        .call();
    } else {
      remove(tag);
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
