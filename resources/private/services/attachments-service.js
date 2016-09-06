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

  function clearData() {
    // to avoid showing stale data before ajax queries return.
    self.attachments([]);
    forceVisibleIds([]);
    self.filters([]);
    self.tagGroups([]);
    self.groupTypes([]);
  }

  var oldAppId = "Not real application id.";
  ko.computed(function() {
    if(self.applicationId() && self.applicationId() !== oldAppId) {
      oldAppId = self.applicationId();
      clearData();  // Just in case
      self.queryAll();
    }
  });

  function queryData(queryName, responseJsonKey, dataSetter, params, hubParams) {
    if (self.authModel.ok(queryName)) {
      var queryParams = _.assign({"id": self.applicationId()}, params);
      ajax.query(queryName, queryParams)
        .success(function(data) {
          dataSetter(data[responseJsonKey]);
          hub.send( self.serviceName + "::query", _.merge({query: queryName,
                                                           key: responseJsonKey},
                                                          params,
                                                          _.omit(hubParams, "eventType")));
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

  function queryTagGroupsAndFilters() {
    queryData("attachments-tag-groups", "tagGroups", self.setTagGroups);
    queryData("attachments-filters", "attachmentsFilters", self.setFilters);
  }

  self.queryAll = function() {
    forceVisibleIds([]);
    queryData("attachments", "attachments", self.setAttachments);
    queryTagGroupsAndFilters();
  };

  // hubParams are attached to the hub send event for attachment query.
  self.queryOne = function(attachmentId, hubParams) {
    (_.get(ko.unwrap(self.getAttachment(attachmentId)), "processing", _.noop))(true);
    queryData("attachment", "attachment", self.setAttachmentData, {"attachmentId": attachmentId}, hubParams);
    queryTagGroupsAndFilters();
  };

  self.getAttachment = function(attachmentId) {
    return _.find(self.attachments(), function(attachment) {
      return attachment.peek().id === attachmentId;
    });
  };

  function orderByTags(attachments, tagGroups) {
    if (_.isEmpty(tagGroups)) {
      return attachments;
    } else {
      return _(tagGroups)
        .map(function(group) {
          var groupAttachments = _.filter(attachments, function(att) {
            return _.includes(att().tags, _.first(group));
          });
          return orderByTags(groupAttachments, _.tail(group));
        })
        .flatten()
        .value();
    }
  }

  var orderedFilteredAttachments = ko.pureComputed(function() {
    return orderByTags(self.filteredAttachments(), self.tagGroups());
  });

  self.nextFilteredAttachmentId = function(attachmentId) {
    var attachments = orderedFilteredAttachments();
    var index = _(attachments).map(ko.unwrap).findIndex(["id", attachmentId]);
    return util.getIn(attachments[index+1], ["id"]);
  };

  self.previousFilteredAttachmentId = function(attachmentId) {
    var attachments = orderedFilteredAttachments();
    var index = _(attachments).map(ko.unwrap).findIndex(["id", attachmentId]);
    return util.getIn(attachments, [index-1, "id"]);
  };

  self.queryGroupTypes = function() {
    queryData("attachment-groups", "groups", self.setGroupTypes);
  };


  function sendHubNotification(eventType, commandName, params, response) {
    hub.send(self.serviceName + "::" + eventType, _.merge({commandName: commandName,
                                                           ok: response.ok,
                                                           response: response},
                                                          params));
  }

  self.removeAttachment = function(attachmentId, hubParams) {
    var params = {id: self.applicationId(), attachmentId: attachmentId};
    ajax.command("delete-attachment", params)
      .success(function(res) {
        self.attachments.remove(function(attachment) {
          return attachment().id === attachmentId;
        });
        self.authModel.refresh({id: self.applicationId()});
        queryTagGroupsAndFilters();
        sendHubNotification("remove", "delete-attachment", _.merge(params, hubParams), res);
      })
      .error(_.partial(sendHubNotification, "remove", "delete-attachment", _.merge(params, hubParams)))
      .processing(self.processing)
      .call();
    return false;
  };
  hub.subscribe("upload-done", function() { self.authModel.refresh({id: self.applicationId()}); });

  self.copyUserAttachments = function(hubParams) {
    var params = {id: self.applicationId()};
    ajax.command("copy-user-attachments-to-application", params)
      .success(function(res) {
        self.queryAll();
        sendHubNotification("copy-user-attachments", "copy-user-attachments-to-application", _.merge(params, hubParams), res);
      })
      .error(_.partial(sendHubNotification, "copy-user-attachments", "copy-user-attachments-to-application", _.merge(params, hubParams)))
      .processing(self.processing)
      .call();
  };

  self.downloadAttachments = function(attachmentIds) {
    var ids = attachmentIds || _(self.attachments()).map(ko.unwrap).map("id");
    var applicationId = self.applicationId();
    var uri = "/api/raw/download-attachments?id=" + applicationId + "&ids=" + ids.join(",") + "&lang=" + loc.getCurrentLanguage();
    window.open(uri);
  };

  self.updateAttachment = function(attachmentId, commandName, params, hubParams) {
    var commandParams = _.assign({"id": self.applicationId(),
                                  "attachmentId": attachmentId},
                                 params);
    ajax.command(commandName, commandParams)
      .success(_.partial(sendHubNotification, "update", commandName, _.merge(commandParams, hubParams)))
      .error(function(response) {
        sendHubNotification("update", commandName, _.merge(commandParams, hubParams), response);
        error("Unable to update attachment: ", response.text);
        notify.ajaxError(response);
      })
      .call();
  };

  self.removeAttachmentVersion = function(attachmentId, fileId, originalFileId, hubParams) {
    self.updateAttachment(attachmentId, "delete-attachment-version", {fileId: fileId, originalFileId: originalFileId}, hubParams);
  };

  self.approveAttachment = function(attachmentId, hubParams) {
    var attachment = self.getAttachment(attachmentId);
    self.updateAttachment(attachmentId, "approve-attachment", {"fileId": util.getIn(attachment, ["latestVersion", "fileId"])}, hubParams);
  };

  self.rejectAttachment = function(attachmentId, hubParams) {
    var attachment = self.getAttachment(attachmentId);
    self.updateAttachment(attachmentId, "reject-attachment", {"fileId": util.getIn(attachment, ["latestVersion", "fileId"])}, hubParams);
  };

  self.setNotNeeded = function(attachmentId, flag, hubParams) {
    forceVisibleIds.push(attachmentId);
    self.updateAttachment(attachmentId, "set-attachment-not-needed", {"notNeeded": Boolean(flag)}, hubParams);
  };

  self.setVisibility = function(attachmentId, visibility, hubParams) {
    self.updateAttachment(attachmentId, "set-attachment-visibility", {"value": visibility}, hubParams);
  };

  self.setMeta = function(attachmentId, metadata, hubParams) {
    self.updateAttachment(attachmentId, "set-attachment-meta", {meta: metadata}, hubParams);
  };

  self.setForPrinting = function(attachmentId, isForPrinting, hubParams) {
    var params = {selectedAttachmentIds: isForPrinting ? [attachmentId] : [],
                  unSelectedAttachmentIds: isForPrinting ? [] : [attachmentId]};
    self.updateAttachment(attachmentId, "set-attachments-as-verdict-attachment", params, hubParams);
  };

  self.rotatePdf = function(attachmentId, rotation, hubParams) {
    self.updateAttachment(attachmentId, "rotate-pdf", {rotation: rotation}, hubParams);
  };

  self.setType = function(attachmentId, type, hubParams) {
    self.updateAttachment(attachmentId, "set-attachment-type", {attachmentType: type}, hubParams);
  };

  self.createAttachmentTemplates = function(types, hubParams) {
    var params =  {id: self.applicationId(), attachmentTypes: types};
    ajax.command("create-attachments", params)
      .success(function(res) {
        self.queryAll();
        sendHubNotification("create", "create-attachments", _.merge(params, hubParams), res);
      })
      .error(_.partial(sendHubNotification, "create", "create-attachments", _.merge(params, hubParams)))
      .processing(self.processing)
      .call();
  };

  function downloadRedirect( uri ) {
    if( location ) {
      location.assign( uri );
    }
  }

  self.downloadAttachments = function(attachmentIds) {
    var ids = attachmentIds || _(self.attachments()).map(ko.unwrap).map("id");
    var applicationId = self.applicationId();
    var uri = "/api/raw/download-attachments?id=" + applicationId + "&ids=" + ids.join(",") + "&lang=" + loc.getCurrentLanguage();
    downloadRedirect( uri );
  };

  function downloadAllAttachments() {
    downloadRedirect("/api/raw/download-all-attachments?id=" + self.applicationId());
  }

  hub.subscribe( self.serviceName + "::downloadAllAttachments", downloadAllAttachments );

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


  // Attachments table icon column. Used both by the regular attachments table and stamping template.
  self.stateIcons = function(attachment) {
    function showSentToCaseManagementIcon(attachment) {
      return attachment.sent && !_.isEmpty(_.filter(lupapisteApp.models.application._js.transfers,
                                                    {type: "attachments-to-asianhallinta"}));
    }

    function showSentIcon(attachment) {
      return attachment.sent && !showSentToCaseManagementIcon(attachment);
    }

    var hasFile = _.get(ko.utils.unwrapObservable(attachment), "latestVersion.fileId");

    function canVouch(attachment) {
      return hasFile && !self.isNotNeeded(attachment);
    }

    var data = ko.utils.unwrapObservable(attachment);
    var notNeeded = attachment.notNeeded();
    var approved = self.isApproved(data) && canVouch(data);
    var rejected = self.isRejected(data) && canVouch(data);

    return _( [[approved, {css: "lupicon-circle-check positive", icon: "approved"}],
               [rejected || (!hasFile && !notNeeded), {css: "lupicon-circle-attention negative",
                                                       icon: "rejected"}],
               [ _.get( data, "signatures.0"), {css: "lupicon-circle-pen positive",
                                                icon: "signed"}],
               [data.state === "requires_authority_action", {css: "lupicon-circle-star primary",
                                                             icon: "state"}],
               [_.get(data, "latestVersion.stamped"), {css: "lupicon-circle-stamp positive",
                                                       icon: "stamped"}],
               [showSentIcon(data), {css: "lupicon-circle-arrow-up positive",
                                     icon: "sent"}],
               [showSentToCaseManagementIcon(data), {css: "lupicon-circle-arrow-up positive",
                                                     icon: "sent-to-case-management"}],
               [attachment.forPrinting(), {css: "lupicon-circle-section-sign positive",
                                          icon: "for-printing"}],
               [_.get( data, "metadata.nakyvyys", "julkinen") !== "julkinen", {css: "lupicon-lock primary",
                                                                               icon: "not-public"}]] )
      .filter(_.first)
      .map(_.last)
      .value();
  };
};
