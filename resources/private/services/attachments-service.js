//
// Provides services for attachments tab.
//
//

LUPAPISTE.AttachmentsService = function() {
  "use strict";
  var self = this;
  ko.options.deferUpdates = true;
  self.APPROVED = "ok";
  self.REJECTED = "requires_user_action";
  self.serviceName = "attachmentsService";

  self.attachments = ko.observableArray([]);
  self.authModels = ko.observable({});
  self.tagGroups = ko.observableArray([]);
  self.groupTypes = ko.observableArray([]);

  // array of arrays of filters received from backend along with default values.
  // [[A B] [C D]] = (and (or A B) (or C D))
  self.filters = ko.observableArray([]);

  self.activeFilters = ko.pureComputed(function() {
    return _.filter(self.filtersArray(), function(f) {
      return f.filter() === true;
    });
  });

  self.filteredAttachments = ko.pureComputed(
    function() {
      return applyFilters(self.attachments(), self.activeFilters());});

  // Ids for attachments that are visible despite of active filters
  var forceVisibleIds = ko.observableArray();

  self.authModel = lupapisteApp.models.applicationAuthModel;
  self.processing = lupapisteApp.models.application.processing;
  self.applicationId = lupapisteApp.models.application.id;

  self.applicationId.subscribe(function(val) {
    // to avoid retaining old filter states when going to different application
    self.internedObservables = {};
  self.authModel.refresh({id: val});
  });

  self.clearData = function() {
    // to avoid showing stale data before ajax queries return, after switching views or applications
    self.attachments([]);
    forceVisibleIds([]);
    self.filters([]);
    self.tagGroups([]);
    self.groupTypes([]);
  };

  function queryData(queryName, responseJsonKey, dataSetter, params, hubParams) {
    if (self.authModel.ok(queryName)) {
      var queryParams = _.assign({"id": self.applicationId()}, params);
      ajax.query(queryName, queryParams)
        .success(function(data) {
          dataSetter(data[responseJsonKey]);
          hub.send( self.serviceName + "::query", _.merge({query: queryName,
                                                           key: responseJsonKey},
                                                          params,
                                                          hubParams));
        })
        .onError("error.unauthorized", notify.ajaxError)
        .call();
    }
  }

  // Initialize self.authModels for attachments. Creates new authorization models or reuses previously created ones.
  function initAuthModels(attachments) {
    self.authModels(_(attachments)
                    .keyBy("id")
                    .mapValues(function(attachment, id) {
                      return self.authModels()[id] || authorization.create();
                    })
                    .value());
  }

  // Refresh all authModels at once.
  function refreshAllAuthModels() {
    authorization.refreshModelsForCategory(self.authModels(), self.applicationId(), "attachments");
  }

  // Refresh authModel for single attachment.
  // This function should NOT be used for refreshing entire set of authModels since
  // one query is produced for each authModel.
  function refreshAuthModel(attachment) {
    var authModel = self.authModels()[attachment.id];
    if (authModel) {
      authModel.refresh(self.applicationId(),
                        {attachmentId: attachment.id,
                         fileId: util.getIn(attachment, ["latestVersion", "fileId"])});
    }
  }

  // Returns authorization model for attachment from self.authModels or creates new authModel and stores it in self.authModels.
  self.getAuthModel = function(attachmentId) {
    var authModel = self.authModels()[attachmentId];
    if (!authModel) {
      authModel = authorization.create();
      self.authModels(_.set(self.authModels(), attachmentId, authModel));
    }
    return authModel;
  };

  function buildAttachmentModel(attachment, attachmentObs) {
    if (ko.isObservable(attachmentObs)) {
      attachmentObs(attachmentObs().reset(attachment));
    } else {
      attachmentObs = ko.observable(new LUPAPISTE.AttachmentModel(attachment, self.getAuthModel(attachment.id)));
    }
    return attachmentObs;
  }

  self.setAttachments = function(attachments) {
    initAuthModels(attachments);
    refreshAllAuthModels();
    self.attachments(_.map(attachments, buildAttachmentModel));
  };

  self.setAttachmentData = function(attachmentData) {
    var existingAttachmentModel = self.getAttachment(attachmentData.id);
    var attachmentModel = buildAttachmentModel(attachmentData, existingAttachmentModel);
    if (!existingAttachmentModel) {
      self.authModels(_.set(self.authModels(), attachmentData.id, attachmentModel().authModel));
      self.attachments.push(attachmentModel);
    }
    refreshAuthModel(attachmentData);
  };

  self.setTagGroups = function(data) {
    self.tagGroups(data);
  };

  self.setFilters = function(data) {
    self.filters(data);
  };

  self.setGroupTypes = function(data) {
    self.groupTypes(data);
  };

  self.queryAttachments = function() {
    queryData("attachments", "attachments", self.setAttachments);
  };

  self.queryAll = function() {
    forceVisibleIds([]);
    queryData("attachments", "attachments", self.setAttachments);
    queryData("attachments-tag-groups", "tagGroups", self.setTagGroups);
    queryData("attachments-filters", "attachmentsFilters", self.setFilters);
  };

  // hubParams are attached to the hub send event for attachment query.
  self.queryOne = function(attachmentId, hubParams) {
    queryData("attachment", "attachment", self.setAttachmentData, {"attachmentId": attachmentId}, hubParams);
    queryData("attachments-tag-groups", "tagGroups", self.setTagGroups);
    queryData("attachments-filters", "attachmentsFilters", self.setFilters);
  };

  self.getAttachment = function(attachmentId) {
    return _.find(self.attachments(), function(attachment) {
      return attachment.peek().id === attachmentId;
    });
  };

  self.nextAttachmentId = function(attachmentId) {
    var index = _(self.filteredAttachments()).map(ko.unwrap).findIndex(["id", attachmentId]);
    return util.getIn(self.filteredAttachments()[index+1], ["id"]);
  };

  self.previousAttachmentId = function(attachmentId) {
    var index = _(self.filteredAttachments()).map(ko.unwrap).findIndex(["id", attachmentId]);
    return util.getIn(self.filteredAttachments()[index-1], ["id"]);
  };

  self.queryGroupTypes = function() {
    queryData("attachment-groups", "groups", self.setGroupTypes);
  };

  self.removeAttachment = function(attachmentId, options) {
    ajax.command("delete-attachment", {id: self.applicationId(), attachmentId: attachmentId})
      .success(function(res) {
        self.attachments.remove(function(attachment) {
          return attachment().id === attachmentId;
        });
        _.get(options, "onSuccess", _.showSavedIndicator)(res);
      })
      .error(_.get(options, "onError", util.showSavedIndicator))
      .complete(_.get(options, "onComplete", _.noop))
      .processing(self.processing)
      .call();
    return false;
  };

  self.copyUserAttachments = function(options) {
    ajax.command("copy-user-attachments-to-application", {id: self.applicationId()})
      .success(function(res) {
        self.queryAll();
        _.get(options, "onSuccess", _.noop)(res);
      })
      .error(_.get(options, "onError", util.showSavedIndicator))
      .complete(_.get(options, "onComplete", _.noop))
      .processing(self.processing)
      .call();
  };

  self.downloadAttachments = function(attachmentIds) {
    var ids = attachmentIds || _(self.attachments()).map(ko.unwrap).map("id");
    var applicationId = self.applicationId();
    var uri = "/api/raw/download-attachments?id=" + applicationId + "&ids=" + ids.join(",") + "&lang=" + loc.getCurrentLanguage();
    window.open(uri);
  };

  self.updateAttachment = function(attachmentId, commandName, params, options) {
    var commandParams = _.assign({"id": self.applicationId(),
                                  "attachmentId": attachmentId},
                                 params);
    ajax.command(commandName, commandParams)
      .success(_.get(options, "onSuccess", util.showSavedIndicator))
      .error(_.get(options, "onError", util.showSavedIndicator))
      .complete(_.get(options, "onComplete", _.noop))
      .call();
  };

  self.removeAttachmentVersion = function(attachmentId, fileId, originalFileId, options) {
    self.updateAttachment(attachmentId, "delete-attachment-version", {fileId: fileId, originalFileId: originalFileId}, options);
  };

  self.approveAttachment = function(attachmentId, options) {
    var attachment = self.getAttachment(attachmentId);
    self.updateAttachment(attachmentId, "approve-attachment", {"fileId": util.getIn(attachment, ["latestVersion", "fileId"])}, options);
  };

  self.rejectAttachment = function(attachmentId, options) {
    var attachment = self.getAttachment(attachmentId);
    self.updateAttachment(attachmentId, "reject-attachment", {"fileId": util.getIn(attachment, ["latestVersion", "fileId"])}, options);
  };

  self.setNotNeeded = function(attachmentId, flag, options) {
    forceVisibleIds.push(attachmentId);
    self.updateAttachment(attachmentId, "set-attachment-not-needed", {"notNeeded": !!flag}, options);
  };

  self.setVisibility = function(attachmentId, visibility, options) {
    self.updateAttachment(attachmentId, "set-attachment-visibility", {"value": visibility}, options);
  };

  self.setMeta = function(attachmentId, metadata, options) {
    self.updateAttachment(attachmentId, "set-attachment-meta", {meta: metadata}, options);
  };

  self.setForPrinting = function(attachmentId, isForPrinting, options) {
    var params = {selectedAttachmentIds: isForPrinting ? [attachmentId] : [],
                  unSelectedAttachmentIds: isForPrinting ? [] : [attachmentId]};
    self.updateAttachment(attachmentId, "set-attachments-as-verdict-attachment", params, options);
  };

  self.rotatePdf = function(attachmentId, rotation, options) {
    self.updateAttachment(attachmentId, "rotate-pdf", {rotation: rotation}, options);
  };

  self.setType = function(attachmentId, type, options) {
    self.updateAttachment(attachmentId, "set-attachment-type", {attachmentType: type}, options);
  };

  self.createAttachmentTempaltes = function(types, options) {
    ajax.command("create-attachments", {id: self.applicationId(), attachmentTypes: types})
      .success(function(res) {
        self.queryAll();
        _.get(options, "onSuccess", util.showSavedIndicator)(res);
      })
      .complete(_.get(options, "onComplete", _.noop))
      .call();
  };

  //helpers for checking relevant attachment states
  self.isApproved = function(attachment) {
    return util.getIn(attachment, ["state"]) === self.APPROVED;
  };
  self.isRejected = function(attachment) {
    return util.getIn(attachment, ["state"]) === self.REJECTED;
  };
  self.isNotNeeded = function(attachment) {
    return util.getIn(attachment, ["notNeeded"]) === true;
  };

  function getUnwrappedAttachmentById(attachmentId) {
    return ko.utils.unwrapObservable(self.getAttachment(attachmentId));
  }

  // returns a function for use in computed
  // If all of the attachments are approved -> approved
  // If some of the attachments are rejected -> rejected
  // Else null
  self.attachmentsStatus = function(attachmentIds) {
    return function() {
      if (_.every(_.map(attachmentIds, getUnwrappedAttachmentById),
                  self.isApproved)) {
        return self.APPROVED;
      } else {
        return _.some(_.map(attachmentIds, getUnwrappedAttachmentById),
                      self.isRejected) ? self.REJECTED : null;
      }
    };
  };


  //
  // Filter manipulation
  //

  self.internedObservables = {};

  // keep track of filter toggles, since they are passed over to the UI, and
  // we need to keep using the same ones after tag updates

  function internFilterBoolean(key, def) {
    if (!self.internedObservables[key]) {
      var filterValue = ko.observable(def);
      self.internedObservables[key] = filterValue;
      filterValue.subscribe(function() {
        forceVisibleIds([]);
      });
    }
    return self.internedObservables[key];
  }

  // filter tag -> observable toggles, shared between service and UI, updated in UI
  self.filtersArray = ko.pureComputed( function() {
    return _.map(_.flatten(self.filters()),
                      function(filter /*, idx // unused */) {
                        return {ltext: "filter." + filter.tag,
                                tag: filter.tag,
                                filter: internFilterBoolean(filter.tag, filter["default"])};});
  });

  self.isAttachmentFiltered = function (att) {
    if (_.includes(forceVisibleIds(), att().id)) {
      return true;
    }
    return _.reduce(self.filters(), function (ok, group) {
          var group_tags = _.map(group, function(x) {return x.tag;});
          var active_tags = _.map(self.activeFilters(), function(x) {return x.tag;});
          var enabled = _.intersection(group_tags, active_tags);
          var tags = att().tags;
          return (ok && (!(_.first(enabled)) || _.first(_.intersection(enabled, tags)))); },  true);
  };

  function applyFilters(attachments /* , active // unused */) {
    return _.filter(attachments, self.isAttachmentFiltered);
  }

  ko.options.deferUpdates = false;
};
