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
  self.REQUIRES_AUTHORITY_ACTION = "requires_authority_action";
  self.JOB_RUNNING = "running";
  self.JOB_DONE = "done";
  self.JOB_ERROR = "error";
  self.JOB_TIMEOUT = "timeout";
  self.serviceName = "attachmentsService";

  self.attachments = ko.observableArray([]);
  self.authModels = ko.observable({});

  self.tagGroups = ko.observableArray([]);
  var tagGroupSets = {};

  // Filters are represented as nested arrays and are interpreted into logical rules as follows:
  // [[A B] [C D]] = (and (or A B) (or C D))
  self.filters = ko.observableArray([]);
  var filterSets = {};

  self.authModel = lupapisteApp.models.applicationAuthModel;
  self.processing = lupapisteApp.models.application.processing;
  self.applicationId = lupapisteApp.models.application.id;

  hub.subscribe( "application-model-updated", function() {
    self.queryAll();
    self.groupTypes([]);
    self.authModel.refresh({id: self.applicationId()});
  });

  hub.subscribe( "contextService::leave", function() {
    clearData();
    self.internedObservables = {};
    self.authModel.setData({});
  });

  function clearData() {
    disposeItems( self.attachments );
    disposeItems(tagGroupSets);
    disposeItems(filterSets);
    self.attachments([]);
    self.attachmentTypes([]);
    filterSets = {};
    self.tagGroups([]);
    tagGroupSets = {};
    self.groupTypes([]);
  }

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
      return true;
    } else {
      return false;
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

  function disposeItems( models ) {
    _.forEach( ko.unwrap(models), function( m ) {
      ko.unwrap( m ).dispose();
    });
  }

  self.setAttachments = function(attachments) {
    disposeItems( self.attachments );
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
    _.forEach(tagGroupSets, function(tagGroupSet) {
      tagGroupSet.setTagGroups(data);
    });
  };

  self.setFilters = function(data) {
    self.filters(data);
    _.forEach(filterSets, function(filterSet) {
      filterSet.setFilters(data);
    });
  };

  self.queryAttachments = function() {
    queryData("attachments", "attachments", self.setAttachments);
  };

  self.queryTagGroupsAndFilters = function() {
    queryData("attachments-tag-groups", "tagGroups", self.setTagGroups);
    queryData("attachments-filters", "attachmentsFilters", self.setFilters);
  };

  self.queryAll = function() {
    _.forEach(filterSets, function(filterSet) { filterSet.resetForcedVisibility(); });
    queryData("attachments", "attachments", self.setAttachments);
    self.queryTagGroupsAndFilters();
  };

  // hubParams are attached to the hub send event for attachment query.
  self.queryOne = function(attachmentId, hubParams) {
    (_.get(ko.unwrap(self.getAttachment(attachmentId)), "processing", _.noop))(true);
    queryData("attachment", "attachment", self.setAttachmentData, {"attachmentId": attachmentId}, hubParams);
  };

  self.getAttachment = function(attachmentId) {
    return _.find(self.attachments(), function(attachment) {
      return attachment.peek().id === attachmentId;
    });
  };

  self.getFilters = function(filterSetId) {
    if (!filterSets[filterSetId]) {
      filterSets[filterSetId] = new LUPAPISTE.AttachmentsFilterSetModel(self.filters());
    }
    return filterSets[filterSetId];
  };

  self.getTagGroups = function(groupSetId) {
    if (!tagGroupSets[groupSetId]) {
      tagGroupSets[groupSetId] = new LUPAPISTE.AttachmentsGroupSetModel(self.tagGroups());
    }
    return tagGroupSets[groupSetId];
  };

  self.hasAttachments = ko.computed(function() {
    var attachments = self.attachments();
    return _.some(attachments, function(a) {return !_.isEmpty(util.getIn(a, ["versions"]));});
  });

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

  self.nextFilteredAttachmentId = function(attachmentId, filterSet) {
    var attachments = orderByTags(filterSet.apply(self.attachments()), self.tagGroups());
    var index = _(attachments).map(ko.unwrap).findIndex(["id", attachmentId]);
    return util.getIn(attachments[index+1], ["id"]);
  };

  self.previousFilteredAttachmentId = function(attachmentId, filterSet) {
    var attachments = orderByTags(filterSet.apply(self.attachments()), self.tagGroups());
    var index = _(attachments).map(ko.unwrap).findIndex(["id", attachmentId]);
    return util.getIn(attachments, [index-1, "id"]);
  };

  self.queryGroupTypes = function() {
    if (!queryData("attachment-groups", "groups", self.groupTypes)) {
      self.groupTypes([]);
    }
  };

  self.groupTypes = ko.observableArray().extend({autoFetch: {fetchFn: self.queryGroupTypes}});

  hub.subscribe("op-description-changed", _.partial(self.groupTypes, []));

  self.queryAttachmentTypes = function() {
    if (!queryData("attachment-types", "attachmentTypes", self.attachmentTypes)) {
      self.attachmentTypes([]);
    }
  };

  self.attachmentTypes = ko.observableArray().extend({autoFetch: {fetchFn: self.queryAttachmentTypes}});

  self.attachmentTypeGroups = ko.pureComputed(function() {
    return _(self.attachmentTypes()).map("type-group").uniq().value();
  });

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
        disposeItems( self.attachments.remove(function(attachment) {
          return attachment().id === attachmentId;
        }));
        self.authModel.refresh({id: self.applicationId()});
        self.queryTagGroupsAndFilters();
        sendHubNotification("remove", "delete-attachment", _.merge(params, hubParams), res);
      })
      .error(_.partial(sendHubNotification, "remove", "delete-attachment", _.merge(params, hubParams)))
      .processing(self.processing)
      .call();
    return false;
  };

  function pollBindJob(statuses, attachments, response) {
    var job = response.job;
    _.forEach( _.values(job.value), function(fileData) {
      if ( statuses[fileData.fileId] ) {
        statuses[fileData.fileId](fileData.status);
      }
    });
    if ( job.status === self.JOB_RUNNING ) {
      ajax.query( "bind-attachments-job", { jobId: job.id,
                                            version: job.version })
        .success( _.partial(pollBindJob, statuses, attachments) )
        .call();
    } else {
      _.forEach(statuses, function(status) {
        if (status === self.JOB_RUNNING) {
          status(self.JOB_TIMEOUT);
        }
      });
      if ( attachments.length === 1 && attachments[0].attachmentId ) {
        self.queryOne(attachments[0].attachmentId);
        self.queryTagGroupsAndFilters();
      } else {
        self.queryAll();
      }
    }
  }

  self.bindAttachments = function(attachments, password) {
    var jobStatuses = _(attachments).map(function(attachment) { return [attachment.fileId, ko.observable(self.JOB_RUNNING)]; }).fromPairs().value();
    ajax.command( "bind-attachments",
                  _.merge( { id: self.applicationId(),
                             filedatas: attachments },
                           _.some(attachments, "sign")  ? {password: password} : {} ))
      .processing( self.processing )
      .success( _.partial(pollBindJob, jobStatuses, attachments) )
      .call();

    return jobStatuses;
  };

  hub.subscribe("upload-done", function(data) {
    self.authModel.refresh({id: self.applicationId()});
    if (data.attachmentId) {
      self.queryOne(data.attachmentId, {triggerCommand: "upload-attachment"});
      self.queryTagGroupsAndFilters();
    } else {
      self.queryAll();
    }
  });

  self.copyUserAttachments = function(hubParams) {
    var params = {id: self.applicationId()};
    ajax.command("copy-user-attachments-to-application", params)
      .success(function(res) {
        self.authModel.refresh({id: self.applicationId()});
        self.queryAll();
        sendHubNotification("copy-user-attachments", "copy-user-attachments-to-application", _.merge(params, hubParams), res);
      })
      .error(_.partial(sendHubNotification, "copy-user-attachments", "copy-user-attachments-to-application", _.merge(params, hubParams)))
      .processing(self.processing)
      .call();
  };

  self.updateAttachment = function(attachmentId, commandName, params, hubParams) {
    var commandParams = _.assign({"id": self.applicationId(),
                                  "attachmentId": attachmentId},
                                 params);
    ajax.command(commandName, commandParams)
      .success(function(response) {
        self.authModel.refresh({id: self.applicationId()});
        sendHubNotification("update", commandName, _.merge(commandParams, hubParams), response);
      })
      .error(function(response) {
        sendHubNotification("update", commandName, _.merge(commandParams, hubParams), response);
        error("Unable to update attachment: " , _.assign({commandName: commandName, commandParams: commandParams}, response));
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
    self.rejectAttachmentNoteEditorState( null );
  };

  self.rejectAttachment = function(attachmentId, hubParams) {
    var attachment = self.getAttachment(attachmentId);
    self.updateAttachment(attachmentId, "reject-attachment", {"fileId": util.getIn(attachment, ["latestVersion", "fileId"])}, hubParams);
    self.rejectAttachmentNoteEditorState( attachmentId );
  };

  self.rejectAttachmentNote = function(attachmentId, note, hubParams) {
    var attachment = self.getAttachment(attachmentId);
    self.updateAttachment(attachmentId,
                          "reject-attachment-note",
                          {fileId: util.getIn(attachment, ["latestVersion", "fileId"]),
                           note: note || ""},
                          hubParams);
    self.rejectAttachmentNoteEditorState( null );
  };

  hub.subscribe( "attachmentsService::update", function( event ) {
    if( event.commandName === "reject-attachment-note" ) {
      self.queryOne( event.attachmentId );
    }
  });

  // Used by reject-note component.
  self.rejectAttachmentNoteEditorState = ko.observable();

  self.setNotNeeded = function(attachmentId, flag, hubParams) {
    _.forEach(filterSets, function(filterSet) { filterSet.forceVisibility(attachmentId); });
    self.updateAttachment(attachmentId, "set-attachment-not-needed", {"notNeeded": Boolean(flag)}, hubParams);
  };

  self.setVisibility = function(attachmentId, visibility, hubParams) {
    self.updateAttachment(attachmentId, "set-attachment-visibility", {"value": visibility}, hubParams);
  };

  self.setConstructionTime = function(attachmentId, value, hubParams) {
    self.updateAttachment(attachmentId, "set-attachment-as-construction-time", {"value": value}, hubParams);
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

  self.createAttachmentTemplates = function(types, group, hubParams) {
    var params =  {id: self.applicationId(), attachmentTypes: types, group: group};
    ajax.command("create-attachments", params)
      .success(function(res) {
        self.queryAll();
        sendHubNotification("create", "create-attachments", _.merge(params, hubParams), res);
      })
      .error(_.partial(sendHubNotification, "create", "create-attachments", _.merge(params, hubParams)))
      .processing(self.processing)
      .call();
  };

  self.convertToPdfA = function(attachmentId) {
    ajax
      .command("convert-to-pdfa", {id: self.applicationId(), attachmentId: attachmentId})
      .success(function() {
        self.queryOne(attachmentId, {triggerCommand: "convert-to-pdfa"});
      })
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




  // If fileId is not given, the approval for the latestVersion is returned.
  // The fileId can be either fileId or originalFileId.
  self.attachmentApproval = function ( attachment, fileId ) {
    if( fileId ) {
      var version = _.find( util.getIn(attachment, ["versions"] ),
                            function( v ) {
                              return v.fileId === fileId
                                || v.originalFileId === fileId;
                            });
      fileId = _.get( version, "originalFileId");
    } else {
      fileId = util.getIn( attachment, ["latestVersion", "originalFileId"]);
    }
    return fileId && util.getIn( attachment, ["approvals", fileId]);
  };

  function attachmentState( attachment ) {
    return _.get( self.attachmentApproval( attachment), "state");
  }

  //helpers for checking relevant attachment states
  self.isApproved = function(attachment) {
    return attachmentState(attachment ) === self.APPROVED;
  };

  self.isRejected = function(attachment ) {
    return attachmentState(attachment) === self.REJECTED
      && !self.isNotNeeded( attachment );
  };
  self.isNotNeeded = function(attachment) {
    return util.getIn(attachment, ["notNeeded"]) === true;
  };
  self.requiresAuthorityAction = function(attachment) {
    return attachmentState(attachment) === self.REQUIRES_AUTHORITY_ACTION;
  };

  // True if the attachment is needed but does not have file yet.
  self.isMissingFile = function( attachment ) {
    return !self.isNotNeeded( attachment )
      && _.isEmpty( util.getIn( attachment, ["versions"]) );
  };

  //
  // Filtering attachments
  //

  self.isFiltered = function(activeFilters, attachment) {
    var att = ko.unwrap(attachment);
    return _.every(activeFilters, function(active_tags) {
      return _.isEmpty(active_tags) || !_.isEmpty(_.intersection(active_tags, att.tags));
    });
  };

  // Active filters are represented as nested arrays.
  // Attachment tags should match into filter by a logical rule interpreted from the filter:
  // [[A B] [C D]] = (and (or A B) (or C D))
  self.applyFilters = function(attachments, activeFilters) {
    return _.filter(attachments, _.partial(self.isFiltered, activeFilters));
  };

  ko.options.deferUpdates = false;

  //
  // Missing required attachments
  //

  function extractMissingAttachments(attachments) {
    var missingAttachments = _.filter(attachments, function(a) {
      var required  = util.getIn( a, ["required"]);
      var notNeeded = util.getIn( a, ["notNeeded"]);
      var noVersions = _.isEmpty( util.getIn( a, ["versions"]));
      return required && !notNeeded && noVersions;
    });
    missingAttachments = _.groupBy(missingAttachments,
                                   _.partialRight(  util.getIn,
                                                   ["type", "type-group"]));
    missingAttachments = _.map(_.keys(missingAttachments), function(k) {
      return [k, missingAttachments[k]];
    });
    return missingAttachments ;
  }

  self.missingRequiredAttachments = ko.pureComputed( function() {
    return extractMissingAttachments( self.attachments());
  });

};
