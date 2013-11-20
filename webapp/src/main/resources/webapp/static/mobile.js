var currentTask = null;

var queue = [];

var loading = false;

var history = [];

MIN_QUEUE_LENGTH = 5;
MAX_HISTORY = 100;

function setCurrentTask(task) {
	currentTask = task;
	if (currentTask != null) {
		$("#input").text(currentTask.input);
		$.mobile.changePage( "#sentimentTask", { transition: "slideup", changeHash: false, allowSamePageTransition: true });
	} else {
		$.mobile.changePage( "#loading", { transition: "slideup", changeHash: false, allowSamePageTransition: true });
	}
}

function setTaskResult(decision) {
	if (currentTask == null) {
		return;
	}
	currentTask.decision = decision;
	$.ajax({
		type: "POST",
		dataType: "json",
		data: JSON.stringify(currentTask),
		url: "/api/tasks",
		contentType: "application/json",
		processData: false,
		success: function( tasks ) {
		}
	});	
	if (queue.length != 0) {
		var task = queue.shift();
		setCurrentTask(task);
	} else {
		setCurrentTask(null);
	}
	
	if (queue.length <= MIN_QUEUE_LENGTH) {
		reloadQueue();
	}
}

function addToQueue(tasks) {
	for (var i = 0; i < tasks.length; i++) {
		var task = tasks[i];
		
		if (history.indexOf(task.id) != -1) {
			continue;
		}
		
		history.push(task.id);
		while (history.length() > MAX_HISTORY) {
			history.shift()
		}
		
		if (queue.length == 0 && currentTask === null) {
			setCurrentTask(task);
		} else {
			var found = false;
			if (currentTask != null && currentTask.id == task.id) {
				found = true;
			}
			for (var j = 0; j < queue.length; j++) {
				if (queue[j].id == task.id) {
					found = true;
					break;
				}
			}
			if (!found) {
				queue.push(task);
			}
		}
	}
	
	if (queue.length <= MIN_QUEUE_LENGTH) {
		reloadQueue();
	}
}

function reloadQueue() {
	if (loading) {
		return;
	}
	
	url = "/api/tasks/assign?n=10";
	for (var i = 0; i < queue.length; i++) {
		var task = queue[i];
		url = url + "&veto=" + task.id;
	}
	
	$.ajax({
		dataType: "json",
		url: url,
		success: function( tasks ) {
			loading = false;
			addToQueue(tasks);
		},
		error: function() {
			loading = false;
			window.setTimeout(function() {
     			reloadQueue();
   			}, 200);
		}
	});
}

$( document ).ready(function() {

$( ".sentiment-button" ).click(function( event ) {
	var sentiment = $(this).data("value");
	setTaskResult(sentiment);
    event.preventDefault();
});

if (queue.length <= MIN_QUEUE_LENGTH) {
	reloadQueue();
}

});