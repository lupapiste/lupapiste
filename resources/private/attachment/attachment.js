/**
 * attachment.js:
 */

var attachment = function() {

	var applicationId;
	var attachmentId;
	var model;

	function createModel() {
		return {
			attachmentId:   ko.observable(),
			type:           ko.observable(),
			filename:       ko.observable(),
			size:           ko.observable(),
			contentType:    ko.observable(),
			application: {
				id:     ko.observable(),
				title:  ko.observable(),
			},
			newFile:        ko.observable(),
			save:           save,
			toApplication: toApplication
		};
	}

	function showAttachment(application) {
		if (!applicationId || !attachmentId) return;
		var attachment = application.attachments && application.attachments[attachmentId];
		if (!attachment) {
			error("Missing attachment: application:", applicationId, "attachment:", attachmentId);
			return;
		}

		var fileUploadForm = document.getElementById("attachmentUploadForm");
		fileUploadForm.reset();
		$(".droparea span").html("Raahaa liite t&auml;h&auml;n");
		model.attachmentId(attachmentId);
		model.type(attachment.type);
		model.filename(attachment.filename);
		model.size(attachment.size);
		model.contentType(attachment.contentType);
		model.application.id(applicationId);
		model.application.title(application.title);
		model.newFile(null);
	}

	hub.subscribe({type: "page-change", pageId: "attachment"}, function(e) {
		applicationId = e.pagePath[0];
		attachmentId = e.pagePath[1];
		repository.getApplication(applicationId, showAttachment);
	});

	hub.subscribe("repository-application-reload", function(e) {
		if (applicationId === e.applicationId) {
			repository.getApplication(applicationId, showAttachment);
		}
	});

	function drop(e) {
		e.stopPropagation();
		e.preventDefault();
		var event = e.originalEvent;
		var files;
		if (typeof event.target.files !== "undefined") {
			files = event.target.files
		} else if (typeof event.dataTransfer !== "undefined" && typeof event.dataTransfer.files !== "undefined") {
			files = event.dataTransfer.files;
		} else {
			debug("No files detected!");
			return false;
		}


		var file = files[0];
		model.filename(file.name);
		model.size(file.size);
		model.contentType(file.type);
		model.newFile(file);
		$(".droparea span").html("Valmis");
		return false;
	}

	// TODO refactor
	function save(m) {
		ajax.command("set-attachment-name", {id: m.application.id(), attachmentId: m.attachmentId(), type: m.type()})
			.success(function() { repository.reloadAllApplications(); })
			.call();
		var file = model.newFile();
		if (file) {
			upload(file);
		}
	}

    function upload(file) {
		$(".droparea span").hide();
		$(".droparea img").show();

		var xhr = new XMLHttpRequest();
		xhr.open("POST", "/rest/upload");

		xhr.upload.addEventListener("progress", function(e) {
				if (e.lengthComputable) {
					var percentComplete = e.loaded / e.total;
					debug("progress", e, percentComplete);
				} else {
					debug("progress", e);
				}
			}, false);
		xhr.upload.addEventListener("load", function() {
				$(".droparea span").show();
				$(".droparea img").hide();
				debug("load", arguments);
				repository.reloadAllApplications(toApplication);
			}, false);
		xhr.upload.addEventListener("error", function() { error("Tiedoston lataaminen ep\u00E4onnistui"); }, false);
		xhr.upload.addEventListener("abort", function() { warn("Tiedoston lataaminen keskeytyi"); }, false);

		var formData = new FormData();
		formData.append("upload", file);
		formData.append("type", model.type());
		formData.append("applicationId", applicationId);
		formData.append("attachmentId", attachmentId);

		xhr.send(formData);
	}

    function toApplication(){
    	window.location.href="#!/application/"+ model.application.id();
    }

	$(function() {
		model = createModel();
		ko.applyBindings(model, $("#attachment")[0]);
		$(".droparea")
			.on("dragover", function(e) { $(this).addClass("droparea-hover"); })
			.on("dragleave", function(e) { $(this).removeClass("droparea-hover"); })
			.on("drop", drop);
		$("#attachment input[type=file]").on("change", drop);
	});

	function newAttachment(m) {
		ajax.command("create-attachment", {id:  m.application.id()})
		.success(function(d) {
			repository.reloadAllApplications(function() {
				window.location.hash = "!/attachment/" + d.applicationId + "/" + d.attachmentId;
			});
		})
		.call();
	}

	return { newAttachment: newAttachment };

}();
