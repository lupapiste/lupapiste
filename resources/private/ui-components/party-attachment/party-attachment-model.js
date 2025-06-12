LUPAPISTE.PartyAttachmentModel = function( params ) {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var att = lupapisteApp.services.attachmentsService;

  self.docId = params.docId;
  self.readOnly = params.readOnly;

  self.pending = ko.observable(false);

  var attTypeId = params.subSchema["attachment-type-id"];
  var targetType = params.subSchema["attachment-target-type"];

  // Go through all application attachments and select first (should be usually only one)
  // that is referencing this suunnittelija document.
  self.currentAttachment = self.disposedPureComputed(function() {
    return ko.unwrap(_(att.attachments()).find(function(a) {
      var t = a.peek().target;
      return t && (t.type === targetType && t.id === self.docId);
    }));
  });

  function refreshValidationAndAuth() {
    // Send empty value to the attachment path, to trigger the
    // cleanup of document approvals by updating the modified timestamp of the field.
    var updates = {paths: [params.path], values: [null]};
    // Send an empty update-doc request to refresh patevyystodistus validation.
    // update-doc will also now refresh the authModel
    params.saveMany(self, updates, null);
  }

  // This will leave the attachment file around, but it will not be
  // connected to the document anymore, and will be removable from the
  // attachments table.
  var removeAttachmentListener = null;
  self.removeAttachment = function() {
    var id = self.currentAttachment().id;
    if (id) {
      att.setTarget(id, {});

      // Wait for setTarget to finish.
      // One off subscription handler used because currentAttachment().id value isn't available in a
      // event handler outside of this fn closure.
      removeAttachmentListener = hub.subscribe({
        eventType: "attachmentsService::update",
        commandName: "set-attachment-target",
        attachmentId: id,
      }, function() {
        refreshValidationAndAuth();

        hub.unsubscribe(removeAttachmentListener);
        removeAttachmentListener = null;
      });
    }
  };

  self.upload = new LUPAPISTE.UploadModel(self, {
    allowMultiple: false,
    readOnly: false,
  });

  // Show spinner when file upload starts
  self.upload.listenService("filesUploading", function() {
    self.pending(true);
  });

  var attachmentsQueryListener = null;

  self.upload.listenService("filesUploaded", function(e) {
    if (e.status === "success") {
      var fileId = e.files[0].fileId;
      var jobStatus = att.bindAttachments([{
        fileId: fileId,
        // This content field is filled using the users locale for regular
        // attachments also. "Muu pätevyystodistus".
        contents: loc(["attachmentType", "osapuolet", attTypeId]),
        group: {groupType: "parties"},
        type: {
          "type-id": attTypeId,
          "type-group": "osapuolet"
        },
        target: {
          "type": targetType,
          "id": self.docId
        },
      }]);
      // Wait for the bind job to finish
      self.disposedSubscribe(jobStatus[fileId], function(newStatus) {
        if (newStatus === att.JOB_DONE) {
          // Need to wait for the queryAll triggered by JOB_DONE status to also finish,
          // so there is no flash of empty field when the new attachment data is not yet available.
          attachmentsQueryListener = hub.subscribe({
            eventType: "attachmentsService::query",
            query: "attachments",
            key: "attachments"
          }, function() {
            self.pending(false);
            refreshValidationAndAuth();

            // Remove the listener once attachments for the application has been
            // loaded once after the bindAttachments job finished.
            hub.unsubscribe(attachmentsQueryListener);
            attachmentsQueryListener = null;
          });
        }
      });
    } else {
      self.pending(false);
    }
  });

  self.addToDisposeQueue(self.upload);
  self.upload.init();
};
