(function() {
"use strict";

function TagModel(tag, saveFn) {
  this.id = tag.id;
  this.label = ko.observable(tag.label);
  this.edit = ko.observable(false);

  this.saveSubscription = this.label.subscribe(function() {
    saveFn();
  });

  this.cancel = function( ctx ) {
    ctx.edit( false );
    ctx.label( ctx.oldLabel );
  };

  this.dispose = function() {
    this.saveSubscription.dispose();
  };
}

function TagsEditorBaseModel(options) {
  var self = this;

  self.rawTags = options.data;
  self.tags = ko.observableArray();

  self.indicator = ko.observable().extend({notify: "always"});

  self.canEdit = lupapisteApp.models.globalAuthModel.ok( options.saveCommandName );

  self.save = _.debounce(function() {
    var tags = _.map( self.tags(), function( t ) {
      return {id: t.id,
              label: _.isBlank( t.label() ) ? t.oldLabel : t.label()};
    }
                      );
    if( _.some( tags, function( t ) {
      return _.isBlank( t.label );
    })) {
      hub.send( "indicator", {style: "negative", message: "error.empty-tag"});
    } else {
      ajax
        .command(options.saveCommandName, {tags: tags})
        .success(function(res) {
          util.showSavedIndicator(res);
          self.refresh();
        })
        .error(util.showSavedIndicator)
        .call();
    }
  }, 500);

  self.refresh = options.refresh;

  self.addTag = function() {
    var tag = new TagModel({id: null, label: ""}, self.save);
    tag.edit(true);
    self.tags.push(tag);
  };

  function remove(tag) {
    tag.edit(false);
    tag.dispose();
    self.tags.remove(tag);
    if (tag.id) {
      self.save();
    }
  }

  function confirmRemoveTagDialog(tag, message) {
    hub.send("show-dialog",
             {ltitle: "tags.deleting",
              size: "medium",
              component: "yes-no-dialog",
              componentParams: {text: message, yesFn: _.partial(remove, tag)}});
  }

  function removeTagFromApplicationsWarning(tag, data) {
    var applications = _.map(data.applications, "id");
    var dialogTextPrefix = loc("tags.removing-from-applications.prefix", tag.label());

    var dialogBody = _.reduce(applications, function(resultStr, idStr) {
      return resultStr + "<div><i>" + idStr + "</i></div>";
    }, "<div class='spacerM'>");
    dialogBody = dialogBody + "</div>";

    var dialogTextSuffix = loc("tags.removing-from-applications.suffix");

    confirmRemoveTagDialog(tag, dialogTextPrefix + dialogBody + dialogTextSuffix);
  }

  self.removeTag = function(tag) {
    if (tag.id) {
      ajax
        .query(options.removeTagOkQueryName, {tagId: tag.id})
        .onError("warning.tags.removing-from-applications", _.partial(removeTagFromApplicationsWarning, tag))
        .success(_.partial(confirmRemoveTagDialog, tag, loc("tags.deleting.confirmation", tag.label())))
        .call();
    } else {
      remove(tag);
    }
  };

  self.editTag = function(item) {
    item.oldLabel = item.label();
    item.edit(true);
  };

  self.onKeypress = function(item, event) {
    if (event.keyCode === 13) {
      item.edit(false);
    }
    return true;
  };

}

LUPAPISTE.TagsEditorModel = function(params) {
  var self = this;

  var orgId = params.organizationId;

  ko.utils.extend(self, new TagsEditorBaseModel({data: lupapisteApp.services.organizationTagsService.data,
                                                 refresh: lupapisteApp.services.organizationTagsService.refresh,
                                                 saveCommandName: "save-organization-tags",
                                                 removeTagOkQueryName: "remove-tag-ok"}));

  function initModels(raw) {
    self.tags(_.map(_.get(raw, [orgId, "tags"]), function(tag) {
      return new TagModel(tag, self.save);
    }));
  }

  initModels(lupapisteApp.services.organizationTagsService.data());

  var rawTagsSubscription = self.rawTags.subscribe(initModels);

  var baseDispose = self.dispose || _.noop;
  self.dispose = function() {
    rawTagsSubscription.dispose();
    baseDispose();
  };
};


LUPAPISTE.CompanyTagsEditorModel = function() {
  var self = this;

  ko.utils.extend(self, new TagsEditorBaseModel({data: lupapisteApp.services.companyTagsService.currentCompanyTags,
                                                 refresh: lupapisteApp.services.companyTagsService.refresh,
                                                 saveCommandName: "save-company-tags",
                                                 removeTagOkQueryName: "remove-company-tag-ok"}));

  function initModels(raw) {
    self.tags(_.map(raw, function(tag) {
      return new TagModel(tag, self.save);
    }));
  }

  initModels(lupapisteApp.services.companyTagsService.currentCompanyTags());

  var rawTagsSubscription = self.rawTags.subscribe(initModels);

  var baseDispose = self.dispose || _.noop;
  self.dispose = function() {
    rawTagsSubscription.dispose();
    baseDispose();
  };
};
})();
