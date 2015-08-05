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
        repository.load(applicationId);
        window.location.hash = "!/application/"+applicationId+"/attachments";
        model.previewDisabled(false);
        return false;
      })
      .onError("error.pre-verdict-attachment", function(e) {
        notify.error(loc(e.text));
      })
      .call();
      hub.send("track-click", {category:"Attachments", label: "", event:"deleteAttachment"});
    return false;
  }

  // this function is mutated over in the attachement.deleteVersion
  var deleteAttachmentVersionFromServerProxy;

  function deleteAttachmentVersionFromServer(fileId) {
    ajax
      .command("delete-attachment-version", {id: applicationId, attachmentId: attachmentId, fileId: fileId})
      .success(function() {
        repository.load(applicationId);
      })
      .error(function() {
        repository.load(applicationId);
      })
      .call();
      hub.send("track-click", {category:"Attachments", label: "", event:"deleteAttachmentVertion"});
    return false;
  }

  function ApproveModel(authorizationModel) {
    var self = this;

    self.authorizationModel = authorizationModel;

    self.setApplication = function(application) { self.application = application; };
    self.setAuthorizationModel = function(authorizationModel) { self.authorizationModel = authorizationModel; };
    self.setAttachmentId = function(attachmentId) { self.attachmentId = attachmentId; };

    self.stateIs = function(state) {
      var att = self.application &&
        _.find(self.application.attachments,
            function(attachment) {
              return attachment.id === self.attachmentId;
            });
      return att.state === state;
    };

    self.isNotOk = function() { return !self.stateIs("ok");};
    self.doesNotRequireUserAction = function() { return !self.stateIs("requires_user_action");};
    self.isApprovable = function() { return self.authorizationModel.ok("approve-attachment"); };
    self.isRejectable = function() { return self.authorizationModel.ok("reject-attachment"); };

    self.rejectAttachment = function() {
      var id = self.application.id;
      ajax.command("reject-attachment", { id: id, attachmentId: self.attachmentId})
        .success(function() {
          repository.load(id);
        })
        .error(function() {
          repository.load(id);
        })
        .call();
        hub.send("track-click", {category:"Attachments", label: "", event:"rejectAttachment"});
      return false;
    };

    self.approveAttachment = function() {
      var id = self.application.id;
      ajax.command("approve-attachment", { id: id, attachmentId: self.attachmentId})
        .success(function() {
          repository.load(id);
        })
        .error(function() {
          repository.load(id);
        })
        .call();
        hub.send("track-click", {category:"Attachments", label: "", event:"approveAttachment"});
      return false;
    };
  }

  var approveModel = new ApproveModel(authorizationModel);

  model = {
    id:                           ko.observable(),
    application:                  applicationModel,
    applicationState:             ko.observable(),
    authorized:                   ko.observable(false),
    filename:                     ko.observable(),
    latestVersion:                ko.observable({}),
    versions:                     ko.observable([]),
    signatures:                   ko.observableArray([]),
    type:                         ko.observable(),
    attachmentType:               ko.observable(),
    allowedAttachmentTypes:       ko.observableArray([]),
    previewDisabled:              ko.observable(false),
    operation:                    ko.observable(),
    selectedOperationId:          ko.observable(),
    selectableOperations:         ko.observableArray(),
    contents:                     ko.observable(),
    scale:                        ko.observable(),
    scales:                       ko.observableArray(LUPAPISTE.config.attachmentScales),
    size:                         ko.observable(),
    sizes:                        ko.observableArray(LUPAPISTE.config.attachmentSizes),
    isVerdictAttachment:          ko.observable(),
    subscriptions:                [],
    indicator:                    ko.observable().extend({notify: "always"}),
    showAttachmentVersionHistory: ko.observable(),
    showHelp:                     ko.observable(false),
    init:                         ko.observable(false),
    groupAttachments:             ko.observableArray(),
    groupIndex:                   ko.observable(),
    changeTypeDialogModel:        undefined,
    metadata:                     ko.observable(),
    showTosMetadata:              ko.observable(false),

    // toggleHelp: function() {
    //   model.showHelp(!model.showHelp());
    // },

    hasPreview: function() {
      return !model.previewDisabled() && (model.isImage() || model.isPdf() || model.isPlainText());
    },

    isImage: function() {
      var version = model.latestVersion();
      if (!version) { return false; }
      var contentType = version.contentType;
      return contentType && contentType.indexOf("image/") === 0;
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
        typeSelector: false
      });

      model.previewDisabled(true);

      // Upload dialog is opened manually here, because click event binding to
      // dynamic content rendered by Knockout is not possible
      LUPAPISTE.ModalDialog.open("#upload-dialog");
    },

    deleteAttachment: function() {
      model.previewDisabled(true);
      hub.send("show-dialog", {ltitle: "attachment.delete.header",
                               size: "medium",
                               component: "yes-no-dialog",
                               componentParams: {ltext: "attachment.delete.message",
                                                 yesFn: deleteAttachmentFromServer}});
    },

    previousAttachment: function() {
      var previousId = util.getIn(model.groupAttachments(), [model.groupIndex() - 1, "id"]);
      if (previousId) {
        window.location.hash = "!/attachment/"+applicationId+"/" + previousId;
        hub.send("track-click", {category:"Attachments", label: "", event:"previousAttachment"});
      }
    },

    nextAttachment: function() {
      var nextId = util.getIn(model.groupAttachments(), [model.groupIndex() + 1, "id"]);
      if (nextId) {
        window.location.hash = "!/attachment/"+applicationId+"/" + nextId;
        hub.send("track-click", {category:"Attachments", label: "", event:"nextAttachment"});
      }
    },

    showChangeTypeDialog: function() {
      model.previewDisabled(true);
      model.changeTypeDialogModel.init(model.attachmentType());
      LUPAPISTE.ModalDialog.open("#change-type-dialog");
    },

    deleteVersion: function(fileModel) {
      var fileId = fileModel.fileId;
      deleteAttachmentVersionFromServerProxy = function() {
        deleteAttachmentVersionFromServer(fileId);
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
    }
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
    return _.contains(LUPAPISTE.config.postVerdictStates, ko.unwrap(model.application.state)) ?
             lupapisteApp.models.currentUser.isAuthority() || _.contains(LUPAPISTE.config.postVerdictStates, ko.unwrap(model.applicationState)) :
             true;
  });

  model.previousAttachmentPresent = ko.pureComputed(function() {
    return model.groupIndex() > 0;
  });

  model.nextAttachmentPresent = ko.pureComputed(function() {
    return model.groupIndex() < model.groupAttachments().length - 1;
  });

  function saveLabelInformation(name, data) {
    data.id = applicationId;
    data.attachmentId = attachmentId;
    ajax
      .command("set-attachment-meta", data)
      .success(function() {
        model.indicator({name: name, type: "saved"});
      })
      .error(function(e) {
        error(e.text);
        model.indicator({name: name, type: "err"});
      })
      .call();
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
            repository.load(applicationId);
          })
          .error(function(e) {
            loader$.hide();
            repository.load(applicationId);
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

    model.subscriptions.push(model.selectedOperationId.subscribe(function(id) {
      if (!model.operation() || id !== model.operation().id) {
        var op = _.findWhere(model.selectableOperations(), {id: id});
        op = op || null;
        saveLabelInformation("operation", {meta: {op: op}});
      }
    }));

    model.subscriptions.push(model.isVerdictAttachment.subscribe(function(isVerdictAttachment) {
      ajax.command("set-attachments-as-verdict-attachment", {
        id: applicationId,
        selectedAttachmentIds: isVerdictAttachment ? [attachmentId] : [],
        unSelectedAttachmentIds: isVerdictAttachment ? [] : [attachmentId]
      })
      .success(function() {
        repository.load(applicationId);
      })
      .error(function(e) {
        error(e.text);
        notify.error(loc("error.dialog.title"), loc("attachment.set-attachments-as-verdict-attachment.error"));
        repository.load(applicationId);
      })
      .call();
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
      error("Missing attachment: application:", applicationId, "attachment:", attachmentId);
      return;
    }

    $("#file-preview-iframe").attr("src","");

    var isUserAuthorizedForAttachment = attachment.required ? lupapisteApp.models.currentUser.role() === "authority" : true;
    model.authorized(isUserAuthorizedForAttachment);

    model.latestVersion(attachment.latestVersion);
    model.versions(attachment.versions);
    model.signatures(attachment.signatures || []);
    model.filename(attachment.filename);
    model.type(attachment.type);
    model.selectableOperations(application.allOperations);
    model.operation(attachment.op);
    model.selectedOperationId(attachment.op ? attachment.op.id : undefined);
    model.contents(attachment.contents);
    model.scale(attachment.scale);
    model.size(attachment.size);
    model.isVerdictAttachment(attachment.forPrinting);
    model.applicationState(attachment.applicationState);
    model.allowedAttachmentTypes(application.allowedAttachmentTypes);
    model.attachmentType(attachmentType(attachment.type["type-group"], attachment.type["type-id"]));
    model.metadata(attachment.metadata);

    model.id(attachmentId);

    approveModel.setApplication(application);
    approveModel.setAttachmentId(attachmentId);

    model.showAttachmentVersionHistory(false);

    pageutil.hideAjaxWait();
    model.indicator(false);

    authorizationModel.refresh(application, {attachmentId: attachmentId}, function() {
      model.init(true);
      if (!model.latestVersion()) {
        setTimeout(function() {
          model.showHelp(true);
        }, 50);
      }
    });

    var rawAttachments = ko.mapping.toJS(model.application.attachments());

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

    subscribe();
  }

  hub.onPageLoad("attachment", function() {
    pageutil.showAjaxWait();
    model.init(false);
    model.showHelp(false);
    applicationId = pageutil.subPage();
    attachmentId = pageutil.lastSubPage();

    if (applicationModel._js.id !== applicationId) {
      repository.load(applicationId);
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

  hub.subscribe({type: "dialog-close", id : "upload-dialog"}, function() {
    resetUploadIframe();
  });

  hub.subscribe("dialog-close", _.partial(model.previewDisabled, false));

  hub.subscribe("side-panel-open", _.partial(model.previewDisabled, true));
  hub.subscribe("side-panel-close", _.partial(model.previewDisabled, false));

  $(function() {
    $("#attachment").applyBindings({
      attachment: model,
      approve: approveModel,
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
      repository.load(uploadingApplicationId);
      LUPAPISTE.ModalDialog.close();
      uploadingApplicationId = null;
    }
  }

  hub.subscribe("upload-done", uploadDone);

  // applicationId, attachmentId, attachmentType, typeSelector, target, locked, authority
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
