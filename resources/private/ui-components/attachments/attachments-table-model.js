LUPAPISTE.AttachmentsTableModel = function(service) {
  "use strict";

  function hasFile(data) {
    return _.get(ko.utils.unwrapObservable(data), "latestVersion.fileId");
  }

  function canVouch( $data ) {
    var data = ko.utils.unwrapObservable( $data );
    return hasFile( data ) && !service.isNotNeeded( data );
  }

  function openAttachment(data) {
    var applicationId = lupapisteApp.models.application._js.id;
    pageutil.openPage("attachment", applicationId + "/" + data.id);
  }

  function stateIcons( $data ) {
    var data = ko.utils.unwrapObservable( $data );
    var notNeeded = service.isNotNeeded( data );
    var file = hasFile( data );
    var approved = service.isApproved( data ) && canVouch( data );
    var rejected = service.isRejected( data ) && canVouch( data );

    return  _( [[approved, "lupicon-circle-check positive"],
                [rejected || (!file && !notNeeded),
                 "lupicon-circle-attention negative"],
                [ _.get( data, "signed.0"), "lupicon-circle-pen positive"],
                [data.state === "requires_authority_action", "lupicon-circle-star primary"],
                [data.stamped, "lupicon-circle-stamp positive"]])
      .map( function( xs ) {
        return _.first( xs ) ? _.last( xs ) : false;
      })
      .filter()
      .value();
  }

  var idPrefix = _.uniqueId("at-input-");
  var appModel = lupapisteApp.models.application;

  // When foo = idFun( fun ), then foo(data) -> fun(data.id)
  var idFun = _.partial( _.flow, _.nthArg(), _.partialRight( _.get, "id" ));

  function statePermitsAttachmentEditing() {
    // restrict post-verdict editing to authorities only
    var appState = appModel.state();
    var stateIsPostVerdict = _.includes(LUPAPISTE.config.postVerdictStates, ko.unwrap(appState));
    if (stateIsPostVerdict) {
      return lupapisteApp.models.currentUser.isAuthority();
    } else {
      return true;
    }
  }

  function canRemove(attachment) {
    return attachment.required ? lupapisteApp.models.currentUser.isAuthority() : true;
  }

  function readOnly(attachmentId) {
    var service = lupapisteApp.services.attachmentsService;
    var attachment = ko.unwrap(service.getAttachment(attachmentId));
    return attachment.readOnly;
  }

  return {
    attachments: service.attachments,
    idPrefix: idPrefix,
    hasFile: hasFile,
    stateIcons: stateIcons,
    inputId: function(index) { return idPrefix + index; },
    isApproved: service.isApproved,
    approve: idFun(service.approve),
    isRejected: service.isRejected,
    reject: idFun(service.reject),
    remove: idFun(service.remove),
    appModel: appModel,
    authModel: lupapisteApp.models.applicationAuthModel,
    isEditable: statePermitsAttachmentEditing,
    canRemove: canRemove,
    readOnly: readOnly,
    canDownload: _.some(service.attachments, function(a) {
      return hasFile(a());
    }),
    isNotNeeded: service.isNotNeeded,
    toggleNotNeeded: function( data  ) {
      service.setNotNeeded( data.id, !data.notNeeded);
    },
    canVouch: canVouch,
    openAttachment: openAttachment
  };
};
