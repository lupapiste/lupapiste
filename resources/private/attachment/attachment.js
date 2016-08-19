var attachment = (function() {
  "use strict";

  var applicationId = null;
  var uploadingApplicationId = null;
  var attachmentId = null;
  var model = null;
  var applicationModel = lupapisteApp.models.application;

  var authorizationModel = authorization.create();
  var signingModel = new LUPAPISTE.SigningModel("#dialog-sign-attachment", false);

  function deleteAttachmentFromServer() {
    ajax
      .command("delete-attachment", {id: applicationId, attachmentId: attachmentId})
      .success(function() {
        applicationModel.reload();
        applicationModel.open("attachments");
        model.previewDisabled(false);
        return false;
      })
      .onError("error.pre-verdict-attachment", notify.ajaxError)
      .call();
      hub.send("track-click", {category:"Attachments", label: "", event:"deleteAttachment"});
    return false;
  }

  // this function is mutated over in the attachement.deleteVersion
  var deleteAttachmentVersionFromServerProxy;

  function deleteAttachmentVersionFromServer(fileId, originalFileId) {
    ajax
      .command("delete-attachment-version", {id: applicationId, attachmentId: attachmentId, fileId: fileId, originalFileId: originalFileId})
      .success(function() {
        repository.load(applicationId, undefined, undefined, true);
      })
      .error(function() {
        repository.load(applicationId, undefined, undefined, true);
      })
      .call();
      hub.send("track-click", {category:"Attachments", label: "", event:"deleteAttachmentVersion"});
    return false;
  }

  var imgRegex = /^image\/(jpeg|png|gif|bmp)$/;

  model = {
    id:                           ko.observable(),
    application:                  applicationModel,
    applicationState:             ko.observable(),
    authorized:                   ko.observable(false),
    state:                        ko.observable(),
    approval:                     ko.observable(),
    filename:                     ko.observable(),
    latestVersion:                ko.observable({}),
    versions:                     ko.observable([]),
    signatures:                   ko.observableArray([]),
    type:                         ko.observable(),
    attachmentType:               ko.observable(),
    previewDisabled:              ko.observable(false),
    previewVisible:               ko.observable(false),
    operation:                    ko.observable(),
    groupType:                    ko.observable(),
    selectedGroup:                ko.observable(),
    selectableGroups:             ko.observableArray(),
    contents:                     ko.observable(),
    scale:                        ko.observable(),
    scales:                       ko.observableArray(LUPAPISTE.config.attachmentScales),
    size:                         ko.observable(),
    sizes:                        ko.observableArray(LUPAPISTE.config.attachmentSizes),
    isVerdictAttachment:          ko.observable(),
    visibility:                   ko.observable(_.head(LUPAPISTE.config.attachmentVisibilities)),
    subscriptions:                [],
    showAttachmentVersionHistory: ko.observable(),
    showHelp:                     ko.observable(false),
    initialized:                  ko.observable(false),
    groupAttachments:             ko.observableArray(),
    groupIndex:                   ko.observable(),
    changeTypeDialogModel:        undefined,
    metadata:                     ko.observable(),
    showTosMetadata:              ko.observable(false),
    dirty:                        false,
    attachmentVisibilities:       ko.observableArray(LUPAPISTE.config.attachmentVisibilities),
    isRamAttachment:              ko.observable(),

    hasPreview: function() {
      return !model.previewDisabled() && (model.isImage() || model.isPdf() || model.isPlainText());
    },

    isImage: function() {
      var version = model.latestVersion();
      if (!version) { return false; }
      var contentType = version.contentType;
      return contentType && imgRegex.test(contentType);
    },

    isPdf: function() {
      var version = model.latestVersion();
      if (!version) { return false; }
      return version.contentType === "application/pdf";
    },

    isPlainText: function() {
      var version = model.latestVersion();
      if (!version) { return false; }
      return version.contentType === "text/plain";
    },

    newAttachmentVersion: function() {
      initFileUpload({
        applicationId: applicationId,
        attachmentId: attachmentId,
        attachmentType: model.attachmentType(),
        typeSelector: false,
        archiveEnabled: authorizationModel.ok("permanent-archive-enabled")
      });

      model.previewDisabled(true);

      // Upload dialog is opened manually here, because click event binding to
      // dynamic content rendered by Knockout is not possible
      LUPAPISTE.ModalDialog.open("#upload-dialog");
    },

    deleteAttachment: function() {
      model.previewDisabled(true);
      var versions = model.versions();
      hub.send("show-dialog", {ltitle: "attachment.delete.header",
                               size: "medium",
                               component: "yes-no-dialog",
                               componentParams: {ltext: _.isEmpty(versions) ? "attachment.delete.message.no-versions" : "attachment.delete.message",
                                                 yesFn: deleteAttachmentFromServer}});
    },

    previousAttachment: function() {
      var previousId = util.getIn(model.groupAttachments(), [model.groupIndex() - 1, "id"]);
      if (previousId) {
        pageutil.openPage("attachment", applicationId + "/" + previousId);
        hub.send("track-click", {category:"Attachments", label: "", event:"previousAttachment"});
      }
    },

    nextAttachment: function() {
      var nextId = util.getIn(model.groupAttachments(), [model.groupIndex() + 1, "id"]);
      if (nextId) {
        pageutil.openPage("attachment", applicationId + "/" + nextId);
        hub.send("track-click", {category:"Attachments", label: "", event:"nextAttachment"});
      }
    },

    newRamAttachment: function() {
      hub.send( "ramService::new", {attachmentId: model.id()} );
    },

    showChangeTypeDialog: function() {
      model.previewDisabled(true);
      model.changeTypeDialogModel.init(model.attachmentType());
      LUPAPISTE.ModalDialog.open("#change-type-dialog");
    },

    deleteVersion: function(fileModel) {
      var fileId = fileModel.fileId;
      var originalFileId = fileModel.originalFileId;
      deleteAttachmentVersionFromServerProxy = function() {
        deleteAttachmentVersionFromServer(fileId, originalFileId);
      };
      model.previewDisabled(true);
      hub.send("show-dialog", {ltitle: "attachment.delete.version.header",
                               size: "medium",
                               component: "yes-no-dialog",
                               componentParams: {ltext: "attachment.delete.version.message",
                                                 yesFn: deleteAttachmentVersionFromServerProxy}});
    },

    sign: function() {
      model.previewDisabled(true);
      signingModel.init({id: applicationId, attachments:[model]});
    },

    toggleAttachmentVersionHistory: function() {
      model.showAttachmentVersionHistory(!model.showAttachmentVersionHistory());
    },

    toggleTosMetadata: function() {
      model.showTosMetadata(!model.showTosMetadata());
    },

    previewUrl: ko.pureComputed(function() {
      var fileId = util.getIn(model, ["latestVersion", "fileId"]);
      return "/api/raw/view-attachment?attachment-id=" + fileId;
    }),

    rotete: function(rotation) {
      var iframe$ = $("#file-preview-iframe");
      iframe$.attr("src","/lp-static/img/ajax-loader.gif");
      ajax.command("rotate-pdf", {id: applicationId, attachmentId: attachmentId, rotation: rotation})
        .success(function() {
          applicationModel.reload();
          hub.subscribe("attachment-loaded", function() {
            model.previewVisible(true);
            iframe$.attr("src", model.previewUrl());
          }, true);
        })
        .call();
    },

    goBackToApplication: function() {
      hub.send("track-click", {category:"Attachments", label: "", event:"backToApplication"});
      model.application.open("attachments");
      if (model.dirty) {
        repository.load(model.application.id(), undefined, undefined, true);
      }
    },

    stateIs: function(state) {
      return model.state() === state;
    },

    rejectAttachment: function() {
      var id = model.application.id();
      var fileId = util.getIn(model, ["latestVersion", "fileId"]);
      ajax.command("reject-attachment", {id: id, fileId: fileId})
        .success(function() {
          model.state("requires_user_action");
          repository.load(applicationId, undefined, undefined, true);
        })
        .call();
        hub.send("track-click", {category:"Attachments", label: "", event:"rejectAttachment"});
      return false;
    },

    approveAttachment: function() {
      var id = model.application.id();
      var fileId = util.getIn(model, ["latestVersion", "fileId"]);
      ajax.command("approve-attachment", {id: id, fileId: fileId})
        .success(function() {
          model.state("ok");
          repository.load(applicationId, undefined, undefined, true);
        })
        .call();
        hub.send("track-click", {category:"Attachments", label: "", event:"approveAttachment"});
      return false;
    },

    getGroupOptionsText: function(item) {
      if (item.groupType === "operation") {
        return item.description ? loc([item.name, "_group_label"]) + " - " + item.description : loc([item.name, "_group_label"]);
      } else if (item.groupType) {
        return loc([item.groupType, "_group_label"]);
      }
    },

    isNotOk: function() { return !model.stateIs("ok");},
    doesNotRequireUserAction: function() { return !model.stateIs("requires_user_action");},
    isApprovable: function() { return authorizationModel.ok("approve-attachment") && model.initialized(); },
    isRejectable: function() { return authorizationModel.ok("reject-attachment") && model.initialized(); }
  };

  model.changeTypeDialogModel = new ChangeTypeDialogModel();
  hub.subscribe("change-attachment-type", function(data) {
    model.attachmentType(data.attachmentType);
  });

  model.name = ko.computed(function() {
    if (model.attachmentType()) {
      return "attachmentType." + model.attachmentType();
    }
    return null;
  });

  model.editable = ko.computed(function() {
    return _.includes(LUPAPISTE.config.postVerdictStates, ko.unwrap(model.application.state)) ?
             lupapisteApp.models.currentUser.isAuthority() || _.includes(LUPAPISTE.config.postVerdictStates, ko.unwrap(model.applicationState)) :
             true;
  });

  model.previewTracking = ko.computed(function() {
    if (model.previewVisible()) {
      hub.send("track-click", {category:"Attachments", label: "", event:"previewVisible"});
    }
  });

  model.previousAttachmentPresent = ko.pureComputed(function() {
    return model.groupIndex() > 0;
  });

  model.nextAttachmentPresent = ko.pureComputed(function() {
    return model.groupIndex() < model.groupAttachments().length - 1;
  });

  model.opSelector = ko.pureComputed(function() {
    var primaryOperation = applicationModel.primaryOperation();
    return util.getIn(primaryOperation, ["attachment-op-selector"]);
  });

  function saveLabelInformation(name, data) {
    if( authorizationModel.ok( "set-attachment-meta" )) {
      data.id = applicationId;
      data.attachmentId = attachmentId;
      ajax
        .command("set-attachment-meta", data)
        .success(function() {
          hub.send("indicator-icon", {style: "positive"});
          model.dirty = true;
        })
        .error(function(e) {
          error("Unable to set attachment-meta", data, e.text);
          notify.ajaxError(e);
        })
        .call();
    }
  }

  function subscribe() {
    model.subscriptions.push(model.attachmentType.subscribe(function(attachmentType) {
      var type = model.type();
      var prevAttachmentType = type["type-group"] + "." + type["type-id"];
      var loader$ = $("#attachment-type-select-loader");
      if (prevAttachmentType !== attachmentType) {
        loader$.show();
        ajax
          .command("set-attachment-type",
            {id:              applicationId,
             attachmentId:    attachmentId,
             attachmentType:  attachmentType})
          .success(function() {
            loader$.hide();
            repository.load(applicationId, undefined, undefined, true);
          })
          .error(function(e) {
            loader$.hide();
            repository.load(applicationId, undefined, undefined, true);
            error(e.text);
          })
          .call();
      }
    }));

    function applySubscription(label) {
      model.subscriptions.push(model[label].subscribe(_.debounce(function(value) {
        if (value || value === "") {
          var data = {};
          data[label] = value;
          saveLabelInformation(label, {meta: data});
        }
      }, 500)));
    }

    model.subscriptions.push(model.selectedGroup.subscribe(function(group) {
      // Update attachment group type + operation when new group is selected
      if (util.getIn(group, ["id"]) !== util.getIn(model, ["operation", "id"]) ||
          util.getIn(group, ["groupType"]) !== util.getIn(model, ["groupType"])) {

        group = _.pick(group, ["id", "name", "groupType"]);
        saveLabelInformation("group", {meta: {group: !_.isEmpty(group) ? group : null}});

        model.operation(util.getIn(group, ["id"]) ? _.omit(group, "groupType") : null);
        model.groupType(util.getIn(group, ["groupType"]));
      }
    }));

    model.subscriptions.push(model.isVerdictAttachment.subscribe(function(isVerdictAttachment) {
      ajax.command("set-attachments-as-verdict-attachment", {
        id: applicationId,
        selectedAttachmentIds: isVerdictAttachment ? [attachmentId] : [],
        unSelectedAttachmentIds: isVerdictAttachment ? [] : [attachmentId]
      })
      .success(function() {
        hub.send("indicator-icon", {style: "positive"});
        repository.load(applicationId, undefined, undefined, true);
      })
      .error(function(e) {
        error(e.text);
        notify.error(loc("error.dialog.title"), loc("attachment.set-attachments-as-verdict-attachment.error"));
        repository.load(applicationId, undefined, undefined, true);
      })
      .call();
    }));

    model.subscriptions.push(model.visibility.subscribe(function(data) {
      if (authorizationModel.ok("set-attachment-visibility")) {
        ajax.command("set-attachment-visibility", {
          id: applicationId,
          attachmentId: model.id(),
          value: data
        })
        .success(function() {
          model.dirty = true;
          hub.send("indicator-icon", {style: "positive"});
        })
        .call();
      }
    }));



    applySubscription("contents");
    applySubscription("scale");
    applySubscription("size");
  }

  function unsubscribe() {
    while(model.subscriptions.length !== 0) {
      model.subscriptions.pop().dispose();
    }
  }

  function attachmentType(groupName, typeName) {
    return [groupName, typeName].join(".");
  }

  function ChangeTypeDialogModel() {
    var self = this;

    function attachmentGroupLabel(groupName) {
      return loc(["attachmentType", groupName, "_group_label"].join("."));
    }

    function attachmentTypeLabel(groupName, typeName) {
      return loc(["attachmentType", attachmentType(groupName, typeName)].join("."));
    }

    self.attachmentType = ko.observable().extend({notify: "always"});
    self.selectableAttachmentTypes = ko.pureComputed(function () {
      return _.map(applicationModel.allowedAttachmentTypes(), function(typeGroup) {
        return {
          groupLabel: attachmentGroupLabel(typeGroup[0]),
          types: _.map(typeGroup[1], function(type) {
            return {
              typeLabel: attachmentTypeLabel(typeGroup[0], type),
              typeValue: attachmentType(typeGroup[0], type)
            };
          })
        };
      });
    });

    self.init = function(currentAttachmentType) {
      self.attachmentType(currentAttachmentType);
    };

    self.ok = function() {
      hub.send("change-attachment-type", {attachmentType: self.attachmentType()});
      LUPAPISTE.ModalDialog.close();
    };
  }

  function showAttachment() {
    model.dirty = false;
    if (!applicationId || !attachmentId ||
        applicationId !== pageutil.subPage() ||
        attachmentId !== pageutil.lastSubPage()) {
      return;
    }

    var application = applicationModel._js;

    lupapisteApp.setTitle(application.title);

    unsubscribe();

    var attachment = _.find(application.attachments, function(value) {return value.id === attachmentId;});
    if (!attachment) {
      pageutil.hideAjaxWait();
      error("Missing attachment: application:", applicationId, "attachment:", attachmentId);
      hub.send("show-dialog", {ltitle: "error.attachment-not-found",
                               size: "small",
                               component: "ok-dialog",
                               componentParams: {ltext: "error.attachment-not-found.desc",
                                                 okFn: _.partial(applicationModel.open, "attachments")}});
      return;
    }

    $("#file-preview-iframe").attr("src","");
    model.previewVisible(false);

    var isUserAuthorizedForAttachment = attachment.required ? lupapisteApp.models.currentUser.role() === "authority" : true;
    model.authorized(isUserAuthorizedForAttachment);

    model.latestVersion(attachment.latestVersion);
    model.versions(attachment.versions);
    model.signatures(attachment.signatures || []);
    model.state(attachment.state);
    model.approval(attachment.approved);
    model.filename(attachment.filename);
    model.type(attachment.type);
    model.operation(attachment.op);
    model.groupType(attachment.groupType);
    model.contents(attachment.contents);
    model.scale(attachment.scale);
    model.size(attachment.size);
    model.isVerdictAttachment(attachment.forPrinting);
    model.visibility(attachment.metadata ? attachment.metadata.nakyvyys : _.head(LUPAPISTE.config.attachmentVisibilities));
    model.applicationState(attachment.applicationState);
    model.attachmentType(attachmentType(attachment.type["type-group"], attachment.type["type-id"]));
    model.metadata(attachment.metadata);

    model.id(attachmentId);

    model.showAttachmentVersionHistory(false);
    model.showTosMetadata(false);
    model.isRamAttachment( Boolean( attachment.ramLink));

    ajax
      .query("attachment-groups", {id: applicationId})
      .success(function(resp) {
        var currentGroup = _.pickBy({groupType: attachment.groupType,
                                     id: util.getIn(attachment, ["op", "id"])},
                                    _.isString);
        // We update group selection only if it has actually changed.
        if( !_.isEqual( resp.groups, model.selectableGroups()) ) {
          model.selectableGroups(resp.groups);
        }
        model.selectedGroup( _.isEmpty(currentGroup)
                             ? null
                             : _.find(model.selectableGroups(), currentGroup));
      })
      .call();

    pageutil.hideAjaxWait();
    authorizationModel.clear();
    authorizationModel.refresh(application, {attachmentId: attachmentId, fileId: util.getIn(model, ["latestVersion", "fileId"])}, function() {
      model.initialized(true);
      if (!model.latestVersion()) {
        setTimeout(function() {
          model.showHelp(true);
        }, 50);
      }
    });

    var rawAttachments = model.application._js.attachments;

    var preAttachments = attachmentUtils.getPreAttachments(rawAttachments);
    var postAttachments = attachmentUtils.getPostAttachments(rawAttachments);

    var preGrouped = attachmentUtils.getGroupByOperation(preAttachments, true, model.application.allowedAttachmentTypes());
    var postGrouped = attachmentUtils.getGroupByOperation(postAttachments, true, model.application.allowedAttachmentTypes());

    var group = _.find(preGrouped.concat(postGrouped), function(g) {
      return _.find(g.attachments, function(att) {
        return att.id === model.id();
      }) !== undefined;
    });

    model.groupAttachments(group.attachments);
    model.groupIndex(_.findIndex(model.groupAttachments(), function(att) {
      return att.id === model.id();
    }));

    hub.send("attachment-loaded");
  }

  hub.subscribe("attachment-loaded", subscribe);

  hub.onPageLoad("attachment", function() {
    pageutil.showAjaxWait();
    model.initialized(false);
    model.showHelp(false);
    applicationId = pageutil.subPage();
    attachmentId = pageutil.lastSubPage();

    if (applicationModel._js.id !== applicationId || model.dirty) {
      repository.load(applicationId, undefined, undefined, true);
    } else {
      showAttachment();
    }
  });

  hub.subscribe("application-model-updated", showAttachment);

  function resetUploadIframe() {
    var originalUrl = $("#uploadFrame").attr("data-src");
    $("#uploadFrame").attr("src", originalUrl);
  }

  hub.subscribe("upload-cancelled", LUPAPISTE.ModalDialog.close);

  hub.subscribe({eventType: "dialog-close", id : "upload-dialog"}, function() {
    resetUploadIframe();
  });

  hub.subscribe("dialog-close", _.partial(model.previewDisabled, false));

  hub.subscribe("side-panel-open", _.partial(model.previewDisabled, true));
  hub.subscribe("side-panel-close", _.partial(model.previewDisabled, false));

  $(function() {
    $("#attachment").applyBindings({
      attachment: model,
      authorization: authorizationModel
    });
    $("#upload-page").applyBindings({});
    $(signingModel.dialogSelector).applyBindings({signingModel: signingModel, authorization: authorizationModel});

    // Iframe content must be loaded AFTER parent JS libraries are loaded.
    // http://stackoverflow.com/questions/12514267/microsoft-jscript-runtime-error-array-is-undefined-error-in-ie-9-while-using
    resetUploadIframe();
  });

  function uploadDone() {
    if (uploadingApplicationId) {
      repository.load(uploadingApplicationId, undefined, undefined, true);
      LUPAPISTE.ModalDialog.close();
      uploadingApplicationId = null;
    }
  }

  hub.subscribe("upload-done", uploadDone);

  function initFileUpload(options) {
    uploadingApplicationId = options.applicationId;
    var iframeId = "uploadFrame";
    var iframe = document.getElementById(iframeId);
    iframe.contentWindow.LUPAPISTE.Upload.init(options);
  }

  function regroupAttachmentTypeList(types) {
    return _.map(types, function(v) { return {group: v[0], types: _.map(v[1], function(t) { return {name: t}; })}; });
  }

  return {
    initFileUpload: initFileUpload,
    regroupAttachmentTypeList: regroupAttachmentTypeList
  };

})();
