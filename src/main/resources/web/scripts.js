document.getElementById('plotButton').addEventListener('click', function() {
    let functionInput = document.getElementById('functionInput').value;
    if (functionInput) {
        plotFunction(functionInput);
    }
});

let scaleX = 20; // Scale factor for x-axis
let scaleY = 20; // Scale factor for y-axis

document.getElementById('zoomInButton').addEventListener('click', function() {
    zoom('in');
});

document.getElementById('zoomOutButton').addEventListener('click', function() {
    zoom('out');
});

function plotFunction(func) {
    let plotDiv = document.getElementById('plot');
    plotDiv.innerHTML = ''; // Clear previous plot

    let canvas = document.createElement('canvas');
    plotDiv.appendChild(canvas);

    let ctx = canvas.getContext('2d');
    let width = canvas.width = plotDiv.clientWidth;
    let height = canvas.height = plotDiv.clientHeight;

    ctx.clearRect(0, 0, width, height);
    ctx.beginPath();

    ctx.moveTo(0, height / 2);

    for (let x = -width / 2; x < width / 2; x += 1) {
        let xValue = x / scaleX;
        let yValue = evaluateFunction(func, xValue);
        let y = height / 2 - yValue * scaleY;
        ctx.lineTo(x + width / 2, y);
    }

    ctx.stroke();
}

function evaluateFunction(func, x) {
    try {
        return math.evaluate(func, { x: x });
    } catch (e) {
        return 0;
    }
}

function zoom(inOut) {
    if (inOut === 'in') {
        scaleX *= 1.2;
        scaleY *= 1.2;
    } else if (inOut === 'out') {
        scaleX /= 1.2;
        scaleY /= 1.2;
    }
    let functionInput = document.getElementById('functionInput').value;
    if (functionInput) {
        plotFunction(functionInput);
    }
}
